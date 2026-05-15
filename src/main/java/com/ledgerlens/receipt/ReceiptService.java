package com.ledgerlens.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlens.config.StorageService;
import com.ledgerlens.ledger.JournalEntryRepository;
import com.ledgerlens.ledger.JournalEntryResponse;
import com.ledgerlens.ledger.LedgerPostingService;
import com.ledgerlens.outbox.OutboxEvent;
import com.ledgerlens.outbox.OutboxEventRepository;
import com.ledgerlens.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final LedgerPostingService ledgerPostingService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public UploadUrlResponse createUpload(UUID userId, String filename) {
        String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeFilename.length() > 200) {
            safeFilename = safeFilename.substring(0, 200);
        }
        String storageKey = "receipts/" + userId + "/" + UUID.randomUUID() + "_" + safeFilename;
        String uploadUrl = storageService.generateUploadUrl(storageKey, 5);

        Receipt receipt = new Receipt();
        receipt.setUser(userRepository.getReferenceById(userId));
        receipt.setOriginalFilename(safeFilename);
        receipt.setStorageKey(storageKey);
        receipt.setStatus(ReceiptStatus.PENDING);
        receiptRepository.save(receipt);

        log.info("Upload URL created: receiptId={} userId={}", receipt.getId(), userId);
        return new UploadUrlResponse(receipt.getId(), uploadUrl, storageKey);
    }

    /**
     * Idempotent variant — same key within 24h returns the cached response
     * without creating a second receipt or MinIO object.
     * Stripe-style: the client generates a UUID key per logical operation
     * and includes it in retries. Safe to call any number of times.
     */
    public UploadUrlResponse createUpload(UUID userId, String filename, String idempotencyKey) {
        String cacheKey = "idempotency:" + userId + ":" + idempotencyKey;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                log.info("Idempotency cache hit: key={} userId={}", idempotencyKey, userId);
                return objectMapper.readValue(cached, UploadUrlResponse.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached idempotency response, creating fresh", e);
            }
        }

        UploadUrlResponse response = createUpload(userId, filename);

        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    24, TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("Failed to cache idempotency response for key={}", idempotencyKey, e);
        }

        return response;
    }

    /**
     * Writes the processing request to the outbox table inside the same transaction
     * that validates the receipt state. The outbox poller publishes to RabbitMQ
     * asynchronously, guaranteeing delivery even if the app crashes after commit.
     */
    @Transactional
    public void triggerProcessing(UUID receiptId, UUID userId) {
        Receipt receipt = receiptRepository.findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Receipt not found: " + receiptId));

        if (receipt.getStatus() != ReceiptStatus.PENDING) {
            throw new IllegalStateException(
                    "Receipt is not in PENDING state, current status: " + receipt.getStatus());
        }

        storageService.assertObjectExists(receipt.getStorageKey());

        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();
        ReceiptProcessingMessage message =
                new ReceiptProcessingMessage(receiptId, receipt.getStorageKey(), userId, traceId);

        try {
            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(receiptId);
            event.setEventType("RECEIPT_PROCESSING_REQUESTED");
            event.setPayload(objectMapper.writeValueAsString(message));
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue receipt for processing", e);
        }

        log.info("Processing enqueued via outbox: receiptId={} userId={}", receiptId, userId);
    }

    @Transactional
    public Page<ReceiptResponse> listReceipts(UUID userId, Pageable pageable) {
        return receiptRepository.findByUserId(userId, pageable).map(this::toResponse);
    }

    @Transactional
    public ReceiptResponse getReceipt(UUID receiptId, UUID userId) {
        return receiptRepository.findByIdAndUserId(receiptId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Receipt not found: " + receiptId));
    }

    private ReceiptResponse toResponse(Receipt receipt) {
        ensureJournalEntry(receipt);
        JournalEntryResponse journalEntry = journalEntryRepository.findByReceiptId(receipt.getId())
                .map(JournalEntryResponse::from)
                .orElse(null);
        return ReceiptResponse.from(receipt, journalEntry);
    }

    private void ensureJournalEntry(Receipt receipt) {
        if (receipt.getStatus() == ReceiptStatus.COMPLETED
                && !journalEntryRepository.existsByReceiptId(receipt.getId())) {
            ledgerPostingService.postReceiptExpense(receipt);
        }
    }

    @Transactional
    public void deleteReceipt(UUID receiptId, UUID userId) {
        Receipt receipt = receiptRepository.findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Receipt not found: " + receiptId));
        String storageKey = receipt.getStorageKey();
        outboxEventRepository.deleteByAggregateId(receiptId);
        int deleted = receiptRepository.deleteByIdAndUserId(receiptId, userId);
        if (deleted == 0) {
            throw new jakarta.persistence.EntityNotFoundException("Receipt not found: " + receiptId);
        }
        deleteStorageObject(storageKey);
        log.info("Receipt deleted: receiptId={} userId={}", receiptId, userId);
    }

    @Transactional
    public int deleteLedger(UUID userId) {
        List<Receipt> receipts = receiptRepository.findAllByUserId(userId);
        receipts.forEach(receipt -> outboxEventRepository.deleteByAggregateId(receipt.getId()));
        int deleted = receiptRepository.deleteByUserId(userId);
        receipts.forEach(receipt -> deleteStorageObject(receipt.getStorageKey()));
        log.warn("Ledger deleted: userId={} receiptCount={}", userId, deleted);
        return deleted;
    }

    private void deleteStorageObject(String storageKey) {
        try {
            storageService.deleteObjectIfExists(storageKey);
        } catch (RuntimeException e) {
            log.warn("Receipt row deleted but storage cleanup failed for key={}", storageKey, e);
        }
    }
}
