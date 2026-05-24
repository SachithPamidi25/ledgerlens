package com.ledgerlens.receipt.cache;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ledgerlens.receipt.MerchantCategory;
import com.ledgerlens.receipt.extraction.ReceiptExtractionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExtractionCacheServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void store_writesExtractionByReceiptHashWithTtl() {
        AiExtractionCacheService cacheService = cacheService();
        ReceiptExtractionResult result = result();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheService.store("hash-123", result);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("ai:extraction:receipt_hash:hash-123"),
                payload.capture(),
                eq(Duration.ofHours(168))
        );
        assertThat(payload.getValue()).contains("\"vendor\":\"Starbucks\"");
    }

    @Test
    void findByContentHash_returnsCachedExtraction() throws Exception {
        AiExtractionCacheService cacheService = cacheService();
        String json = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build()
                .writeValueAsString(result());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ai:extraction:receipt_hash:hash-123")).thenReturn(json);

        Optional<ReceiptExtractionResult> cached = cacheService.findByContentHash("hash-123");

        assertThat(cached).isPresent();
        assertThat(cached.get().vendor()).isEqualTo("Starbucks");
        assertThat(cached.get().receiptDate()).isEqualTo(LocalDate.of(2026, 4, 2));
    }

    @Test
    void findByContentHash_malformedJsonReturnsEmpty() {
        AiExtractionCacheService cacheService = cacheService();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ai:extraction:receipt_hash:hash-123")).thenReturn("not-json");

        assertThat(cacheService.findByContentHash("hash-123")).isEmpty();
    }

    private AiExtractionCacheService cacheService() {
        AiExtractionCacheService cacheService = new AiExtractionCacheService(
                redisTemplate,
                JsonMapper.builder().addModule(new JavaTimeModule()).build()
        );
        ReflectionTestUtils.setField(cacheService, "cacheTtlHours", 168L);
        return cacheService;
    }

    private ReceiptExtractionResult result() {
        return new ReceiptExtractionResult(
                "Starbucks",
                MerchantCategory.FOOD,
                LocalDate.of(2026, 4, 2),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                "USD",
                java.util.List.of()
        );
    }
}
