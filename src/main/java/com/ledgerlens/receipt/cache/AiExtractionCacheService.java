package com.ledgerlens.receipt.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlens.receipt.extraction.ReceiptExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiExtractionCacheService {

    private static final String KEY_PREFIX = "ai:extraction:receipt_hash:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.extraction.cache-ttl-hours:168}")
    private long cacheTtlHours;

    public Optional<ReceiptExtractionResult> findByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }

        String cached = redisTemplate.opsForValue().get(cacheKey(contentHash));
        if (cached == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(cached, ReceiptExtractionResult.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize cached AI extraction for hash={}: {}", contentHash, e.getMessage());
            return Optional.empty();
        }
    }

    public void store(String contentHash, ReceiptExtractionResult result) {
        if (contentHash == null || contentHash.isBlank() || result == null) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(
                    cacheKey(contentHash),
                    objectMapper.writeValueAsString(result),
                    Duration.ofHours(cacheTtlHours)
            );
        } catch (Exception e) {
            log.warn("Failed to cache AI extraction for hash={}", contentHash, e);
        }
    }

    private String cacheKey(String contentHash) {
        return KEY_PREFIX + contentHash;
    }
}
