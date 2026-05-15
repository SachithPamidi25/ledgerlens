package com.ledgerlens.receipt;

import com.ledgerlens.config.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptProcessingServiceTest {

    @Mock private ReceiptRepository receiptRepository;
    @Mock private ClaudeVisionService claudeVisionService;
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
        when(claudeVisionService.extractReceiptData(imageBytes)).thenReturn(result);

        ReceiptStatus status = processingService.process(message);

        assertThat(status).isEqualTo(ReceiptStatus.COMPLETED);
        verify(persistenceService).markProcessing(receiptId);
        verify(persistenceService).persistResult(receiptId, hash, result);
        verify(claudeVisionService).extractReceiptData(imageBytes);
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
        verifyNoInteractions(claudeVisionService);
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
        when(claudeVisionService.extractReceiptData(imageBytes))
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
        verifyNoInteractions(claudeVisionService);
    }
}
