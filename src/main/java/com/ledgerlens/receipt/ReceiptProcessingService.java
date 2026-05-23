package com.ledgerlens.receipt;

import com.ledgerlens.config.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptProcessingService {

    private final ReceiptRepository receiptRepository;
    private final AiExtractionClient aiExtractionClient;
    private final AiExtractionCacheService aiExtractionCacheService;
    private final AiOutputSanitizer aiOutputSanitizer;
    private final AiObservabilityService aiObservabilityService;
    private final ReceiptExtractionValidator extractionValidator;
    private final StorageService storageService;
    private final ReceiptPersistenceService persistenceService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Orchestrator — no @Transactional.
     * External I/O (MinIO, Claude) never holds a DB connection.
     *
     * Dedup strategy (layered):
     *   1. DB check: fast path for already-stored duplicates
     *   2. Redis lock: prevents two concurrent workers from both calling Claude
     *      for the same image (race condition that the DB check alone misses)
     *   3. DB constraint uq_receipts_content_hash_user: final safety net
     */
    public ReceiptStatus process(ReceiptProcessingMessage message) {
        persistenceService.markProcessing(message.receiptId());

        byte[] imageBytes = storageService.downloadBytes(message.storageKey());
        String contentHash = storageService.computeContentHashFromBytes(imageBytes);

        // Layer 1: DB duplicate check (fast path — already committed duplicate)
        if (receiptRepository.existsByContentHashAndUserId(contentHash, message.userId())) {
            log.info("Duplicate detected via DB check: user={} hash={}", message.userId(), contentHash);
            persistenceService.markDuplicate(message.receiptId(), contentHash);
            return ReceiptStatus.DUPLICATE;
        }

        // Layer 2: Distributed lock — prevents concurrent workers calling Claude for
        // the same content hash. Value is the receiptId so we can identify the lock holder.
        String lockKey = "dedup_lock:" + message.userId() + ":" + contentHash;
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, message.receiptId().toString(), 10, TimeUnit.MINUTES);

        if (!Boolean.TRUE.equals(lockAcquired)) {
            String lockHolder = redisTemplate.opsForValue().get(lockKey);
            log.info("Concurrent duplicate detected via distributed lock: " +
                    "receiptId={} lockHolder={} hash={}", message.receiptId(), lockHolder, contentHash);
            persistenceService.markDuplicate(message.receiptId(), contentHash);
            return ReceiptStatus.DUPLICATE;
        }

        String provider = metricLabel(aiExtractionClient.providerName());
        String model = metricLabel(aiExtractionClient.modelName());
        long extractionStart = System.nanoTime();

        try {
            ReceiptExtractionResult result;
            boolean cacheHit = false;
            var cachedExtraction = aiExtractionCacheService.findByContentHash(contentHash);
            if (cachedExtraction.isPresent()) {
                result = cachedExtraction.get();
                cacheHit = true;
                aiObservabilityService.recordCacheHit(provider, model);
                log.info("AI extraction cache hit: provider={} model={} requestId={} receiptId={} hash={}",
                        provider, model, message.traceId(), message.receiptId(), contentHash);
            } else {
                try {
                    result = aiExtractionClient.extractReceiptData(imageBytes);
                    long latencyMs = elapsedMs(extractionStart);
                    aiObservabilityService.recordExtractionLatency(latencyMs, provider, model);
                    aiObservabilityService.recordExtractionSuccess(provider, model);
                    log.info("AI extraction succeeded: provider={} model={} requestId={} receiptId={} latencyMs={}",
                            provider, model, message.traceId(), message.receiptId(), latencyMs);
                } catch (RuntimeException e) {
                    long latencyMs = elapsedMs(extractionStart);
                    aiObservabilityService.recordExtractionLatency(latencyMs, provider, model);
                    aiObservabilityService.recordExtractionFailure(provider, model, e.getClass().getSimpleName());
                    if (isTimeout(e)) {
                        aiObservabilityService.recordProviderTimeout(provider, model);
                    }
                    log.warn("AI extraction failed: provider={} model={} requestId={} receiptId={} latencyMs={} reason={}",
                            provider, model, message.traceId(), message.receiptId(), latencyMs, e.getMessage());
                    throw e;
                }
            }

            var sanitizedExtraction = aiOutputSanitizer.sanitize(result);
            result = sanitizedExtraction.result();
            List<ReceiptExtractionValidationError> securityErrors = sanitizedExtraction.securityErrors();
            if (!securityErrors.isEmpty()) {
                aiObservabilityService.recordPromptInjectionDetected(provider, model);
            }

            List<ReceiptExtractionValidationError> validationErrors = extractionValidator.validate(result);
            if (!validationErrors.isEmpty()) {
                aiObservabilityService.recordSchemaValidationFailure(provider, model);
            }

            var reviewErrors = new ArrayList<>(securityErrors);
            reviewErrors.addAll(validationErrors);
            if (!reviewErrors.isEmpty()) {
                persistenceService.markNeedsReview(message.receiptId(), contentHash, result, reviewErrors);
                log.warn("AI extraction requires review: provider={} model={} requestId={} receiptId={} validationResult=failed errorCount={}",
                        provider, model, message.traceId(), message.receiptId(), reviewErrors.size());
                return ReceiptStatus.NEEDS_REVIEW;
            }
            persistenceService.persistResult(message.receiptId(), contentHash, result);
            if (!cacheHit) {
                aiExtractionCacheService.store(contentHash, result);
            }
            log.info("AI extraction accepted: provider={} model={} requestId={} receiptId={} validationResult=passed",
                    provider, model, message.traceId(), message.receiptId());
            return ReceiptStatus.COMPLETED;
        } finally {
            // Always release the lock — even on failure, so the next retry can proceed
            redisTemplate.delete(lockKey);
        }
    }

    public void markFailed(UUID receiptId) {
        persistenceService.markFailed(receiptId);
    }

    public void markDuplicate(UUID receiptId, String contentHash) {
        persistenceService.markDuplicate(receiptId, contentHash);
    }

    public void markPermanentlyFailed(UUID receiptId) {
        persistenceService.markPermanentlyFailed(receiptId);
    }

    private long elapsedMs(long startNanos) {
        return Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String metricLabel(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
