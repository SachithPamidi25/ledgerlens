package com.ledgerlens.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlens.config.StorageService;
import com.ledgerlens.ledger.JournalEntry;
import com.ledgerlens.ledger.JournalEntryRepository;
import com.ledgerlens.ledger.JournalEntryResponse;
import com.ledgerlens.ledger.JournalEntryType;
import com.ledgerlens.ledger.LedgerPostingService;
import com.ledgerlens.outbox.OutboxEvent;
import com.ledgerlens.outbox.OutboxEventRepository;
import com.ledgerlens.receipt.processing.ReceiptProcessingMessage;
import com.ledgerlens.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {
    private static final Set<String> SUPPORTED_RECEIPT_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Set<ReceiptStatus> RETRYABLE_PROCESSING_STATUSES =
            Set.of(ReceiptStatus.FAILED, ReceiptStatus.PERMANENTLY_FAILED);
    private static final DateTimeFormatter MONTH_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

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
        validateSupportedReceiptFilename(filename);
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

    private void validateSupportedReceiptFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Receipt filename is required");
        }

        int extensionStart = filename.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == filename.length() - 1) {
            throw new IllegalArgumentException("Receipt file must be PNG, JPG, WEBP, or GIF");
        }

        String extension = filename.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_RECEIPT_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported receipt file type. Upload PNG, JPG, WEBP, or GIF");
        }
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

        enqueueProcessing(receipt, userId, "Processing enqueued via outbox");
    }

    @Transactional
    public void retryProcessing(UUID receiptId, UUID userId) {
        Receipt receipt = receiptRepository.findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Receipt not found: " + receiptId));

        if (!RETRYABLE_PROCESSING_STATUSES.contains(receipt.getStatus())) {
            throw new IllegalStateException(
                    "Receipt is not retryable, current status: " + receipt.getStatus());
        }

        receipt.setStatus(ReceiptStatus.PENDING);
        receipt.setFailureReason(null);
        enqueueProcessing(receipt, userId, "Receipt processing retry enqueued via outbox");
    }

    private void enqueueProcessing(Receipt receipt, UUID userId, String logMessage) {
        storageService.assertObjectExists(receipt.getStorageKey());

        UUID receiptId = receipt.getId();
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

        log.info("{}: receiptId={} userId={}", logMessage, receiptId, userId);
    }

    @Transactional
    public Page<ReceiptResponse> listReceipts(UUID userId, Pageable pageable) {
        return receiptRepository.findByUserId(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReceiptStatusSummaryResponse summarizeStatuses(UUID userId) {
        List<Receipt> receipts = receiptRepository.findAllByUserId(userId);
        long completed = receipts.stream().filter(receipt -> receipt.getStatus() == ReceiptStatus.COMPLETED).count();
        long processing = receipts.stream()
                .filter(receipt -> receipt.getStatus() == ReceiptStatus.PENDING
                        || receipt.getStatus() == ReceiptStatus.PROCESSING)
                .count();
        long failed = receipts.stream()
                .filter(receipt -> receipt.getStatus() == ReceiptStatus.FAILED
                        || receipt.getStatus() == ReceiptStatus.PERMANENTLY_FAILED)
                .count();
        long needsReview = receipts.stream().filter(receipt -> receipt.getStatus() == ReceiptStatus.NEEDS_REVIEW).count();
        long duplicate = receipts.stream().filter(receipt -> receipt.getStatus() == ReceiptStatus.DUPLICATE).count();
        BigDecimal completedSpend = receipts.stream()
                .filter(receipt -> receipt.getStatus() == ReceiptStatus.COMPLETED)
                .map(receipt -> receipt.getTotal() == null ? BigDecimal.ZERO : receipt.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReceiptStatusSummaryResponse(
                receipts.size(),
                completed,
                processing,
                failed,
                needsReview,
                duplicate,
                completedSpend
        );
    }

    @Transactional(readOnly = true)
    public List<ReceiptExpensePeriodResponse> summarizeExpensePeriods(UUID userId, String mode) {
        boolean yearly = normalizeExpensePeriodMode(mode);
        Map<String, List<Receipt>> receiptsByPeriod = receiptRepository.findCompletedForSummary(userId)
                .stream()
                .collect(Collectors.groupingBy(receipt -> yearly
                        ? String.valueOf(receipt.getReceiptDate().getYear())
                        : receipt.getReceiptDate().format(MONTH_KEY_FORMATTER)));

        return receiptsByPeriod.entrySet()
                .stream()
                .map(entry -> toExpensePeriodResponse(entry.getKey(), entry.getValue(), yearly))
                .sorted(Comparator.comparing(ReceiptExpensePeriodResponse::key).reversed())
                .toList();
    }

    private boolean normalizeExpensePeriodMode(String mode) {
        if (mode == null || mode.isBlank() || "monthly".equalsIgnoreCase(mode)) {
            return false;
        }
        if ("yearly".equalsIgnoreCase(mode)) {
            return true;
        }
        throw new IllegalArgumentException("Expense period mode must be monthly or yearly");
    }

    private ReceiptExpensePeriodResponse toExpensePeriodResponse(String key, List<Receipt> receipts, boolean yearly) {
        BigDecimal total = receipts.stream()
                .map(receipt -> receipt.getTotal() == null ? BigDecimal.ZERO : receipt.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReceiptExpensePeriodResponse(
                key,
                yearly ? key : monthLabel(receipts.getFirst()),
                yearly ? "Year" : "Month",
                receipts.size(),
                total
        );
    }

    private String monthLabel(Receipt receipt) {
        String month = receipt.getReceiptDate().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return month + " " + receipt.getReceiptDate().getYear();
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
        List<JournalEntryResponse> journalEntries = journalEntryRepository.findByReceiptIdOrderByCreatedAtDesc(receipt.getId())
                .stream()
                .map(JournalEntryResponse::from)
                .toList();
        JournalEntryResponse journalEntry = journalEntries.stream()
                .filter(entry -> entry.entryType() == JournalEntryType.ORIGINAL
                        || entry.entryType() == JournalEntryType.CORRECTION)
                .findFirst()
                .orElse(null);
        return ReceiptResponse.from(receipt, journalEntry, journalEntries);
    }

    @Transactional
    public ReceiptResponse correctReceipt(UUID receiptId, UUID userId, ReceiptCorrectionRequest request) {
        Receipt receipt = receiptRepository.findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Receipt not found: " + receiptId));

        if (receipt.getStatus() != ReceiptStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Only completed receipts can be corrected, current status: " + receipt.getStatus());
        }

        JournalEntry activeEntry = journalEntryRepository
                .findFirstByReceiptIdAndEntryTypeInOrderByCreatedAtDesc(
                        receiptId, List.of(JournalEntryType.ORIGINAL, JournalEntryType.CORRECTION))
                .orElseThrow(() -> new IllegalStateException("Receipt has no posted journal entry to correct"));

        JournalEntry reversal = ledgerPostingService.reverseEntry(activeEntry, request.reason());
        applyCorrection(receipt, request);
        ledgerPostingService.postReceiptCorrection(receipt, reversal, request.reason());
        return toResponse(receipt);
    }

    private void ensureJournalEntry(Receipt receipt) {
        if (receipt.getStatus() == ReceiptStatus.COMPLETED
                && !journalEntryRepository.existsByReceiptIdAndEntryTypeIn(
                        receipt.getId(), List.of(JournalEntryType.ORIGINAL, JournalEntryType.CORRECTION))) {
            ledgerPostingService.postReceiptExpense(receipt);
        }
    }

    private void applyCorrection(Receipt receipt, ReceiptCorrectionRequest request) {
        if (request.vendor() != null) {
            receipt.setVendor(request.vendor().isBlank() ? null : request.vendor().trim());
        }
        if (request.merchantCategory() != null) {
            receipt.setMerchantCategory(request.merchantCategory());
        }
        if (request.receiptDate() != null) {
            receipt.setReceiptDate(request.receiptDate());
        }
        if (request.subtotal() != null) {
            receipt.setSubtotal(request.subtotal());
        }
        if (request.tax() != null) {
            receipt.setTax(request.tax());
        }
        if (request.tip() != null) {
            receipt.setTip(request.tip());
        }
        if (request.total() != null) {
            receipt.setTotal(request.total());
        }
        if (request.currency() != null) {
            receipt.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
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
