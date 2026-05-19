package com.ledgerlens.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlens.config.StorageService;
import com.ledgerlens.ledger.JournalEntry;
import com.ledgerlens.ledger.JournalEntryRepository;
import com.ledgerlens.ledger.JournalEntryType;
import com.ledgerlens.ledger.LedgerPostingService;
import com.ledgerlens.outbox.OutboxEventRepository;
import com.ledgerlens.user.User;
import com.ledgerlens.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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

    @Test
    void correctReceipt_reversesOriginalEntryAndPostsCorrection() {
        UUID userId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Receipt receipt = new Receipt();
        receipt.setId(receiptId);
        receipt.setUser(user);
        receipt.setOriginalFilename("receipt.jpg");
        receipt.setStorageKey("receipts/key/receipt.jpg");
        receipt.setStatus(ReceiptStatus.COMPLETED);
        receipt.setVendor("Old Vendor");
        receipt.setMerchantCategory(MerchantCategory.FOOD);
        receipt.setTotal(new BigDecimal("12.50"));
        receipt.setCurrency("USD");

        JournalEntry activeEntry = new JournalEntry();
        activeEntry.setReceipt(receipt);
        activeEntry.setUser(user);
        activeEntry.setEntryType(JournalEntryType.ORIGINAL);
        activeEntry.setCorrectionSequence(0);
        JournalEntry reversal = new JournalEntry();
        reversal.setReceipt(receipt);
        reversal.setUser(user);
        reversal.setEntryType(JournalEntryType.REVERSAL);
        reversal.setCorrectionSequence(1);

        ReceiptCorrectionRequest request = new ReceiptCorrectionRequest(
                "Correct Vendor",
                MerchantCategory.TRANSPORT,
                null,
                null,
                null,
                null,
                new BigDecimal("18.75"),
                "inr",
                "AI read the wrong total"
        );

        when(receiptRepository.findByIdAndUserId(receiptId, userId)).thenReturn(Optional.of(receipt));
        when(journalEntryRepository.findFirstByReceiptIdAndEntryTypeInOrderByCreatedAtDesc(
                eq(receiptId), eq(List.of(JournalEntryType.ORIGINAL, JournalEntryType.CORRECTION))))
                .thenReturn(Optional.of(activeEntry));
        when(ledgerPostingService.reverseEntry(activeEntry, request.reason())).thenReturn(reversal);
        when(journalEntryRepository.existsByReceiptIdAndEntryTypeIn(
                eq(receiptId), eq(List.of(JournalEntryType.ORIGINAL, JournalEntryType.CORRECTION))))
                .thenReturn(true);
        when(journalEntryRepository.findByReceiptIdOrderByCreatedAtDesc(receiptId)).thenReturn(List.of());

        receiptService.correctReceipt(receiptId, userId, request);

        assertThat(receipt.getVendor()).isEqualTo("Correct Vendor");
        assertThat(receipt.getMerchantCategory()).isEqualTo(MerchantCategory.TRANSPORT);
        assertThat(receipt.getTotal()).isEqualByComparingTo("18.75");
        assertThat(receipt.getCurrency()).isEqualTo("INR");
        InOrder inOrder = inOrder(ledgerPostingService);
        inOrder.verify(ledgerPostingService).reverseEntry(activeEntry, request.reason());
        inOrder.verify(ledgerPostingService).postReceiptCorrection(receipt, reversal, request.reason());
    }
}
