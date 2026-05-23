package com.ledgerlens.insights;

import com.ledgerlens.receipt.MerchantCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroundedSpendingQaServiceTest {

    @Mock private ReceiptSemanticIndexService receiptSemanticIndexService;

    @InjectMocks
    private GroundedSpendingQaService service;

    @Test
    void answerQuestion_buildsGroundedAnswerAndRecordsSources() {
        UUID userId = UUID.randomUUID();
        String question = "Why did my food spending increase in April?";
        List<ReceiptSearchResult> sources = List.of(
                source("Starbucks", MerchantCategory.FOOD, "8.50", 0.12),
                source("Zomato", MerchantCategory.FOOD, "22.00", 0.18),
                source("Amazon", MerchantCategory.SHOPPING, "14.00", 0.29)
        );
        when(receiptSemanticIndexService.search(userId, question, 5)).thenReturn(sources);

        SpendingQuestionResponse response = service.answerQuestion(userId, question, 5);

        assertThat(response.answer())
                .contains("FOOD")
                .contains("USD 30.50")
                .contains("Starbucks")
                .contains("Zomato");
        assertThat(response.sources()).hasSize(3);
        verify(receiptSemanticIndexService).recordInsightSources(userId, question, response.answer(), sources);
    }

    @Test
    void answerQuestion_returnsEmptyResponseWhenNoSourcesExist() {
        UUID userId = UUID.randomUUID();
        when(receiptSemanticIndexService.search(userId, "coffee near college", 3)).thenReturn(List.of());

        SpendingQuestionResponse response = service.answerQuestion(userId, "coffee near college", 3);

        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).contains("could not find completed receipts");
    }

    @Test
    void answerQuestion_blankQuestionDoesNotSearch() {
        SpendingQuestionResponse response = service.answerQuestion(UUID.randomUUID(), " ", 5);

        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).contains("Ask a spending question");
        verifyNoInteractions(receiptSemanticIndexService);
    }

    private ReceiptSearchResult source(String vendor, MerchantCategory category, String total, double distance) {
        return new ReceiptSearchResult(
                UUID.randomUUID(),
                vendor,
                category,
                LocalDate.of(2026, 4, 12),
                new BigDecimal(total),
                "USD",
                vendor + " " + category.name(),
                distance
        );
    }
}
