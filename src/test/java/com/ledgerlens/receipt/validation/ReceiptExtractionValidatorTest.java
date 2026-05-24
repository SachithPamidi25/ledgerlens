package com.ledgerlens.receipt.validation;

import com.ledgerlens.receipt.MerchantCategory;
import com.ledgerlens.receipt.extraction.ReceiptExtractionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptExtractionValidatorTest {

    private final ReceiptExtractionValidator validator = new ReceiptExtractionValidator();

    @Test
    void validate_validReceipt_returnsNoErrors() {
        ReceiptExtractionResult result = new ReceiptExtractionResult(
                "Starbucks",
                MerchantCategory.FOOD,
                LocalDate.now(),
                new BigDecimal("10.00"),
                new BigDecimal("0.80"),
                new BigDecimal("1.20"),
                new BigDecimal("12.00"),
                "USD",
                List.of(new ReceiptExtractionResult.LineItem("Latte", 1, new BigDecimal("10.00")))
        );

        assertThat(validator.validate(result)).isEmpty();
    }

    @Test
    void validate_missingRequiredFields_returnsReviewErrors() {
        ReceiptExtractionResult result = new ReceiptExtractionResult(
                " ",
                MerchantCategory.FOOD,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(validator.validate(result))
                .extracting(ReceiptExtractionValidationError::field)
                .contains("vendor", "receiptDate", "currency", "total");
    }

    @Test
    void validate_amountMismatchAndNegativeLineItem_returnsErrors() {
        ReceiptExtractionResult result = new ReceiptExtractionResult(
                "Cafe",
                MerchantCategory.FOOD,
                LocalDate.now(),
                new BigDecimal("10.00"),
                new BigDecimal("2.00"),
                BigDecimal.ZERO,
                new BigDecimal("9.00"),
                "INR",
                List.of(new ReceiptExtractionResult.LineItem("Discount bug", 1, new BigDecimal("-1.00")))
        );

        assertThat(validator.validate(result))
                .extracting(ReceiptExtractionValidationError::field)
                .contains("total", "lineItems[0].price");
    }
}
