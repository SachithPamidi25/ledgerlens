package com.ledgerlens.agent;

import com.ledgerlens.receipt.MerchantCategory;
import com.ledgerlens.receipt.Receipt;
import com.ledgerlens.receipt.ReceiptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadOnlyFinanceAgentServiceTest {

    @Mock private ReceiptRepository receiptRepository;
    @Mock private AiToolCallAuditService auditService;

    @InjectMocks
    private ReadOnlyFinanceAgentService service;

    @Test
    void answer_routesMonthlySpendToAllowedToolAndAudits() {
        UUID userId = UUID.randomUUID();
        when(receiptRepository.findCompletedForSummaryBetween(
                eq(userId), eq(LocalDate.of(2026, 4, 1)), eq(LocalDate.of(2026, 5, 1))))
                .thenReturn(List.of(receipt("Starbucks", MerchantCategory.FOOD, "8.50")));

        FinanceAgentResponse response = service.answer(userId, "What did I spend in 2026-04?");

        assertThat(response.toolName()).isEqualTo("getMonthlySpend");
        assertThat(response.answer()).contains("8.50");
        assertThat(response.result()).containsEntry("receiptCount", 1);
        verify(auditService).record(eq(userId), eq("getMonthlySpend"), eq(Map.of("month", "2026-04")), eq("SUCCESS"), anyLong());
    }

    @Test
    void answer_refusesMutationRequestsWithoutCallingTools() {
        FinanceAgentResponse response = service.answer(UUID.randomUUID(), "Delete previous ledger entries");

        assertThat(response.toolName()).isNull();
        assertThat(response.answer()).contains("read-only tools");
        verifyNoInteractions(receiptRepository, auditService);
    }

    @Test
    void answer_routesDuplicateSearchAndAudits() {
        UUID userId = UUID.randomUUID();
        Receipt first = receipt("Amazon", MerchantCategory.SHOPPING, "20.00");
        Receipt second = receipt("Amazon", MerchantCategory.SHOPPING, "20.00");
        first.setContentHash("same-hash");
        second.setContentHash("same-hash");
        when(receiptRepository.findAllByUserId(userId)).thenReturn(List.of(first, second));

        FinanceAgentResponse response = service.answer(userId, "Find duplicate receipts");

        assertThat(response.toolName()).isEqualTo("findDuplicateReceipts");
        assertThat(response.answer()).contains("1 duplicate receipt group");
        verify(auditService).record(eq(userId), eq("findDuplicateReceipts"), eq(Map.of()), eq("SUCCESS"), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void answer_routesHighValueTransactionToolWithParsedThreshold() {
        UUID userId = UUID.randomUUID();
        when(receiptRepository.findAllByUserId(userId)).thenReturn(List.of(
                receipt("Zomato", MerchantCategory.FOOD, "42.00"),
                receipt("Apple", MerchantCategory.SHOPPING, "220.00")
        ));

        FinanceAgentResponse response = service.answer(userId, "List high value transactions over 100");

        assertThat(response.toolName()).isEqualTo("listHighValueTransactions");
        List<Map<String, Object>> transactions = (List<Map<String, Object>>) response.result().get("transactions");
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst()).containsEntry("merchant", "Apple");

        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(userId), eq("listHighValueTransactions"), argsCaptor.capture(), eq("SUCCESS"), anyLong());
        assertThat(argsCaptor.getValue()).containsEntry("threshold", new BigDecimal("100"));
    }

    private Receipt receipt(String vendor, MerchantCategory category, String total) {
        Receipt receipt = new Receipt();
        receipt.setId(UUID.randomUUID());
        receipt.setVendor(vendor);
        receipt.setMerchantCategory(category);
        receipt.setReceiptDate(LocalDate.of(2026, 4, 12));
        receipt.setTotal(new BigDecimal(total));
        receipt.setCurrency("USD");
        return receipt;
    }
}
