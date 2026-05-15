package com.ledgerlens.receipt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReceiptValidationService {

    private static final BigDecimal MAX_TOTAL = new BigDecimal("1000000");
    private static final int MAX_VENDOR_LENGTH = 500;

    /**
     * Validates the structured data Claude extracted from a receipt.
     * Throws ClaudeNonRetryableException on business-rule violations —
     * these indicate a bad image or hallucination, not a transient API issue,
     * so retrying would waste credits.
     */
    public void validate(ReceiptExtractionResult result) {
        List<String> violations = new ArrayList<>();

        if (result.total() != null) {
            if (result.total().compareTo(BigDecimal.ZERO) < 0)
                violations.add("total is negative (" + result.total() + ")");
            if (result.total().compareTo(MAX_TOTAL) > 0)
                violations.add("total exceeds maximum (" + result.total() + ")");
        }

        if (result.subtotal() != null && result.total() != null
                && result.subtotal().compareTo(result.total()) > 0)
            violations.add("subtotal (" + result.subtotal() + ") exceeds total (" + result.total() + ")");

        if (result.tax() != null && result.tax().compareTo(BigDecimal.ZERO) < 0)
            violations.add("tax is negative");

        if (result.tip() != null && result.tip().compareTo(BigDecimal.ZERO) < 0)
            violations.add("tip is negative");

        if (result.receiptDate() != null && result.receiptDate().isAfter(LocalDate.now()))
            violations.add("receipt date is in the future (" + result.receiptDate() + ")");

        if (result.receiptDate() != null && result.receiptDate().isBefore(LocalDate.of(1970, 1, 1)))
            violations.add("receipt date is implausibly old (" + result.receiptDate() + ")");

        if (result.vendor() != null && result.vendor().length() > MAX_VENDOR_LENGTH)
            violations.add("vendor name exceeds " + MAX_VENDOR_LENGTH + " characters");

        if (!violations.isEmpty()) {
            String message = "Claude response failed validation: " + violations;
            log.warn(message);
            throw new ClaudeNonRetryableException(message);
        }

        log.debug("Claude response passed validation: vendor={} total={}", result.vendor(), result.total());
    }
}
