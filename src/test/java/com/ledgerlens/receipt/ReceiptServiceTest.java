package com.ledgerlens.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlens.config.StorageService;
import com.ledgerlens.ledger.JournalEntryRepository;
import com.ledgerlens.ledger.LedgerPostingService;
import com.ledgerlens.outbox.OutboxEventRepository;
import com.ledgerlens.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock private ReceiptRepository receiptRepository;
    @Mock private StorageService storageService;
    @Mock private UserRepository userRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private LedgerPostingService ledgerPostingService;
    @Mock private ObjectMapper objectMapper;
    @Mock private StringRedisTemplate redisTemplate;

    @InjectMocks
    private ReceiptService receiptService;

    @Test
    void createUpload_rejectsPdfBeforeCreatingPresignedUrl() {
        assertThatThrownBy(() -> receiptService.createUpload(UUID.randomUUID(), "receipt.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported receipt file type");

        verifyNoInteractions(storageService, receiptRepository);
    }
}
