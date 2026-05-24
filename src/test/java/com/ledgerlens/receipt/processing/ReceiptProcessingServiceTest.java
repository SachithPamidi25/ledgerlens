package com.ledgerlens.receipt.processing;

import com.ledgerlens.config.StorageService;
import com.ledgerlens.receipt.MerchantCategory;
import com.ledgerlens.receipt.ReceiptPersistenceService;
import com.ledgerlens.receipt.ReceiptRepository;
import com.ledgerlens.receipt.ReceiptStatus;
import com.ledgerlens.receipt.cache.AiExtractionCacheService;
import com.ledgerlens.receipt.extraction.AiExtractionClient;
import com.ledgerlens.receipt.extraction.ReceiptExtractionResult;
import com.ledgerlens.receipt.observability.AiObservabilityService;
import com.ledgerlens.receipt.security.AiOutputSanitizer;
import com.ledgerlens.receipt.validation.ReceiptExtractionValidationError;
import com.ledgerlens.receipt.validation.ReceiptExtractionValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptProcessingServiceTest {

    @Mock private ReceiptRepository receiptRepository;
    @Mock private AiExtractionClient aiExtractionClient;
    @Mock private AiExtractionCacheService aiExtractionCacheService;
    @Mock private AiOutputSanitizer aiOutputSanitizer;
    @Mock private AiObservabilityService aiObservabilityService;
    @Mock private ReceiptExtractionValidator extractionValidator;
    @Mock private StorageService storageService;
    @Mock private ReceiptPersistenceService persistenceService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ReceiptProcessingService processingService;

    private final UUID receiptId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final ReceiptProcessingMessage message = new ReceiptProcessingMessage(
            receiptId, "receipts/key/file.jpg", userId, UUID.randomUUID().toString());

    @Test
    void process_uniqueReceipt_returnsCompleted() {
        byte[] imageBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        String hash = "abc123";
        ReceiptExtractionResult result = new ReceiptExtractionResult(
                "Starbucks", MerchantCategory.FOOD, null,
                null, null, null, null, "USD", null);

        when(storageService.downloadBytes(anyString())).thenReturn(imageBytes);
        when(storageService.computeContentHashFromBytes(imageBytes)).thenReturn(hash);
        when(receiptRepository.existsByContentHashAndUserId(hash, userId)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("dedup_lock:" + userId + ":" + hash), eq(receiptId.toString()), eq(10L), any()))
                .thenReturn(true);
        when(aiExtractionCacheService.findByContentHash(hash)).thenReturn(Optional.empty());
        when(aiExtractionClient.extractReceiptData(imageBytes)).thenReturn(result);
        when(aiOutputSanitizer.sanitize(result))
                .thenReturn(new AiOutputSanitizer.SanitizedExtraction(result, List.of()));
        when(extractionValidator.validate(result)).thenReturn(List.of());

        ReceiptStatus status = processingService.process(message);

        assertThat(status).isEqualTo(ReceiptStatus.COMPLETED);
        verify(persistenceService).markProcessing(receiptId);
        verify(persistenceService).persistResult(receiptId, hash, result);
        verify(aiExtractionCacheService).store(hash, result);
        verify(aiExtractionClient).extractReceiptData(imageBytes);
        verify(redisTemplate).delete("dedup_lock:" + userId + ":" + hash);
    }

    @Test
    void process_cachedExtraction_skipsAiCallAndPersistsResult() {
        byte[] imageBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        String hash = "cached-hash";
        ReceiptExtractionResult cached = new ReceiptExtractionResult(
                "Starbucks", MerchantCategory.FOOD, java.time.LocalDate.of(2026, 4, 2),
                java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.TEN, "USD", List.of());

        when(storageService.downloadBytes(anyString())).thenReturn(imageBytes);
        when(storageService.computeContentHashFromBytes(imageBytes)).thenReturn(hash);
        when(receiptRepository.existsByContentHashAndUserId(hash, userId)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("dedup_lock:" + userId + ":" + hash), eq(receiptId.toString()), eq(10L), any()))
                .thenReturn(true);
        when(aiExtractionCacheService.findByContentHash(hash)).thenReturn(Optional.of(cached));
        when(aiOutputSanitizer.sanitize(cached))
                .thenReturn(new AiOutputSanitizer.SanitizedExtraction(cached, List.of()));
        when(extractionValidator.validate(cached)).thenReturn(List.of());

        ReceiptStatus status = processingService.process(message);

        assertThat(status).isEqualTo(ReceiptStatus.COMPLETED);
        verify(aiObservabilityService).recordCacheHit("unknown", "unknown");
        verify(aiExtractionClient, never()).extractReceiptData(any());
        verify(aiExtractionCacheService, never()).store(anyString(), any());
        verify(persistenceService).persistResult(receiptId, hash, cached);
        verify(redisTemplate).delete("dedup_lock:" + userId + ":" + hash);
    }

    @Test
    void process_invalidExtraction_marksNeedsReviewWithoutPostingLedger() {
        byte[] imageBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        String hash = "review-hash";
        ReceiptExtractionResult result = new ReceiptExtractionResult(
                "", MerchantCategory.FOOD, null,
                null, null, null, null, "USD", null);
        List<ReceiptExtractionValidationError> errors =
                List.of(new ReceiptExtractionValidationError("vendor", "merchant is required"));

        when(storageService.downloadBytes(anyString())).thenReturn(imageBytes);
        when(storageService.computeContentHashFromBytes(imageBytes)).thenReturn(hash);
        when(receiptRepository.existsByContentHashAndUserId(hash, userId)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("dedup_lock:" + userId + ":" + hash), eq(receiptId.toString()), eq(10L), any()))
                .thenReturn(true);
        when(aiExtractionCacheService.findByContentHash(hash)).thenReturn(Optional.empty());
        when(aiExtractionClient.extractReceiptData(imageBytes)).thenReturn(result);
        when(aiOutputSanitizer.sanitize(result))
                .thenReturn(new AiOutputSanitizer.SanitizedExtraction(result, List.of()));
        when(extractionValidator.validate(result)).thenReturn(errors);

        ReceiptStatus status = processingService.process(message);

        assertThat(status).isEqualTo(ReceiptStatus.NEEDS_REVIEW);
        verify(persistenceService).markNeedsReview(receiptId, hash, result, errors);
        verify(persistenceService, never()).persistResult(any(), any(), any());
        verify(aiExtractionCacheService, never()).store(anyString(), any());
        verify(redisTemplate).delete("dedup_lock:" + userId + ":" + hash);
    }

    @Test
    void process_duplicateHash_returnsDuplicateWithoutCallingClaude() {
        byte[] imageBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        String hash = "duplicate-hash";

        when(storageService.downloadBytes(anyString())).thenReturn(imageBytes);
        when(storageService.computeContentHashFromBytes(imageBytes)).thenReturn(hash);
        when(receiptRepository.existsByContentHashAndUserId(hash, userId)).thenReturn(true);

        ReceiptStatus status = processingService.process(message);

        assertThat(status).isEqualTo(ReceiptStatus.DUPLICATE);
        verify(persistenceService).markDuplicate(receiptId, hash);
        verifyNoInteractions(aiExtractionClient);
    }

    @Test
    void process_claudeFails_propagatesException() {
        byte[] imageBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        String hash = "hash-xyz";

        when(storageService.downloadBytes(anyString())).thenReturn(imageBytes);
        when(storageService.computeContentHashFromBytes(imageBytes)).thenReturn(hash);
        when(receiptRepository.existsByContentHashAndUserId(hash, userId)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("dedup_lock:" + userId + ":" + hash), eq(receiptId.toString()), eq(10L), any()))
                .thenReturn(true);
        when(aiExtractionCacheService.findByContentHash(hash)).thenReturn(Optional.empty());
        when(aiExtractionClient.extractReceiptData(imageBytes))
                .thenThrow(new RuntimeException("Claude API timeout"));

        assertThatThrownBy(() -> processingService.process(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Claude API timeout");

        verify(persistenceService).markProcessing(receiptId);
        verify(persistenceService, never()).persistResult(any(), any(), any());
        verify(redisTemplate).delete("dedup_lock:" + userId + ":" + hash);
    }

    @Test
    void process_storageDownloadFails_propagatesException() {
        when(storageService.downloadBytes(anyString()))
                .thenThrow(new RuntimeException("MinIO connection refused"));

        assertThatThrownBy(() -> processingService.process(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MinIO connection refused");

        verify(persistenceService).markProcessing(receiptId);
        verifyNoInteractions(aiExtractionClient);
    }
}
