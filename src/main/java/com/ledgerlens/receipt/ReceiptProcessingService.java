package com.ledgerlens.receipt;

import com.ledgerlens.config.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptProcessingService {

    private final ReceiptRepository receiptRepository;
    private final ClaudeVisionService claudeVisionService;
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

        try {
            ReceiptExtractionResult result = claudeVisionService.extractReceiptData(imageBytes);
            persistenceService.persistResult(message.receiptId(), contentHash, result);
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
}
