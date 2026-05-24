package com.ledgerlens.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlens.insights.ReceiptSemanticIndexService;
import com.ledgerlens.ledger.LedgerPostingService;
import com.ledgerlens.merchant.MerchantNormalizationService;
import com.ledgerlens.receipt.extraction.ReceiptExtractionResult;
import com.ledgerlens.receipt.validation.ReceiptExtractionValidationError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptPersistenceService {

    private final ReceiptRepository receiptRepository;
    private final MerchantNormalizationService merchantNormalizationService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final LedgerPostingService ledgerPostingService;
    private final ReceiptSemanticIndexService receiptSemanticIndexService;

    @Transactional
    public void markProcessing(UUID receiptId) {
        receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Receipt not found: " + receiptId))
                .setStatus(ReceiptStatus.PROCESSING);
        publishStatusAfterCommit(receiptId, ReceiptStatus.PROCESSING);
    }

    @Transactional
    public void persistResult(UUID receiptId, String contentHash, ReceiptExtractionResult result) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Receipt not found: " + receiptId));

        applyExtraction(receipt, contentHash, result);
        receipt.setStatus(ReceiptStatus.COMPLETED);
        ledgerPostingService.postReceiptExpense(receipt);
        receiptSemanticIndexService.indexReceipt(receipt);
        publishStatusAfterCommit(receiptId, ReceiptStatus.COMPLETED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markNeedsReview(
            UUID receiptId,
            String contentHash,
            ReceiptExtractionResult result,
            List<ReceiptExtractionValidationError> validationErrors
    ) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Receipt not found: " + receiptId));

        applyExtraction(receipt, contentHash, result);
        receipt.setStatus(ReceiptStatus.NEEDS_REVIEW);

        try {
            Map<String, Object> reviewPayload = new LinkedHashMap<>();
            reviewPayload.put("extraction", result);
            reviewPayload.put("validationErrors", validationErrors);
            receipt.setRawExtraction(objectMapper.writeValueAsString(reviewPayload));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize review extraction for receipt {}", receiptId, e);
        }

        publishStatusAfterCommit(receiptId, ReceiptStatus.NEEDS_REVIEW);
        log.warn("Receipt {} requires review before ledger posting: {}", receiptId, validationErrors);
    }

    private void applyExtraction(Receipt receipt, String contentHash, ReceiptExtractionResult result) {
        receipt.setContentHash(contentHash);
        if (result == null) {
            return;
        }

        String normalizedVendor = merchantNormalizationService.normalize(result.vendor());
        receipt.setVendor(normalizedVendor);
        receipt.setMerchantCategory(result.merchantCategory());
        receipt.setReceiptDate(result.receiptDate());
        receipt.setSubtotal(result.subtotal());
        receipt.setTax(result.tax());
        receipt.setTip(result.tip());
        receipt.setTotal(result.total());
        if (result.currency() != null) {
            receipt.setCurrency(result.currency());
        }

        try {
            receipt.setRawExtraction(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize raw extraction for receipt {}", receipt.getId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID receiptId) {
        receiptRepository.findById(receiptId).ifPresent(r -> r.setStatus(ReceiptStatus.FAILED));
        publishStatusAfterCommit(receiptId, ReceiptStatus.FAILED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPermanentlyFailed(UUID receiptId) {
        receiptRepository.findById(receiptId).ifPresent(r -> r.setStatus(ReceiptStatus.PERMANENTLY_FAILED));
        publishStatusAfterCommit(receiptId, ReceiptStatus.PERMANENTLY_FAILED);
        log.error("ALERT: Receipt {} has been permanently failed after DLQ retries exhausted", receiptId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDuplicate(UUID receiptId, String contentHash) {
        receiptRepository.findById(receiptId).ifPresent(r -> {
            r.setStatus(ReceiptStatus.DUPLICATE);
            if (contentHash != null) {
                r.setContentHash(contentHash);
            }
        });
        publishStatusAfterCommit(receiptId, ReceiptStatus.DUPLICATE);
    }

    /**
     * Publishes the status event to Redis pub/sub AFTER the transaction commits.
     * Using afterCommit() prevents SSE clients from seeing a status that was
     * rolled back — the DB is the source of truth, pub/sub is a notification layer.
     */
    private void publishStatusAfterCommit(UUID receiptId, ReceiptStatus status) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    String channel = "receipt.status." + receiptId;
                    String payload = objectMapper.writeValueAsString(
                            Map.of("receiptId", receiptId.toString(), "status", status.name()));
                    redisTemplate.convertAndSend(channel, payload);
                } catch (Exception e) {
                    log.warn("Failed to publish status event for receipt {}", receiptId, e);
                }
            }
        });
    }
}
