package com.ledgerlens.receipt;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptCorrectionRequest(
        @Size(max = 255) String vendor,
        MerchantCategory merchantCategory,
        LocalDate receiptDate,
        @DecimalMin(value = "0.00") BigDecimal subtotal,
        @DecimalMin(value = "0.00") BigDecimal tax,
        @DecimalMin(value = "0.00") BigDecimal tip,
        @DecimalMin(value = "0.01") BigDecimal total,
        @Size(max = 10) String currency,
        @Size(max = 500) String reason
) {
}
