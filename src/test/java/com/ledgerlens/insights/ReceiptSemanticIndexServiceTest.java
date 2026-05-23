package com.ledgerlens.insights;

import com.ledgerlens.ai.LocalTextEmbeddingService;
import com.ledgerlens.receipt.MerchantCategory;
import com.ledgerlens.receipt.Receipt;
import com.ledgerlens.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptSemanticIndexServiceTest {

    private final ReceiptSemanticIndexService service =
            new ReceiptSemanticIndexService(new LocalTextEmbeddingService());

    @Test
    void buildSearchText_includesGroundingFields() {
        User user = new User();
        user.setId(UUID.randomUUID());
        Receipt receipt = new Receipt();
        receipt.setUser(user);
        receipt.setVendor("Starbucks");
        receipt.setMerchantCategory(MerchantCategory.FOOD);
        receipt.setReceiptDate(LocalDate.of(2026, 4, 10));
        receipt.setTotal(new BigDecimal("8.50"));
        receipt.setCurrency("USD");
        receipt.setRawExtraction("{\"lineItems\":[{\"name\":\"latte\"}]}");

        String text = service.buildSearchText(receipt);

        assertThat(text)
                .contains("Starbucks")
                .contains("FOOD")
                .contains("2026-04-10")
                .contains("8.50")
                .contains("USD")
                .contains("latte");
    }
}
