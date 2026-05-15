package com.ledgerlens.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceiptExtractionResult(
        String vendor,
        MerchantCategory merchantCategory,
        LocalDate receiptDate,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal tip,
        BigDecimal total,
        String currency,
        List<LineItem> lineItems
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineItem(
            String name,
            Integer quantity,
            BigDecimal price
    ) {}
}