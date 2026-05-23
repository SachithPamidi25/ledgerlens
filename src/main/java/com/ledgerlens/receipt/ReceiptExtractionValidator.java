package com.ledgerlens.receipt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class ReceiptExtractionValidator {

    private static final BigDecimal MAX_TOTAL = new BigDecimal("1000000");
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.05");
    private static final int MAX_VENDOR_LENGTH = 500;
    private static final Set<String> SUPPORTED_CURRENCIES =
            Set.of("INR", "USD", "EUR", "GBP", "AUD", "CAD", "SGD", "LKR");
    private static final EnumSet<MerchantCategory> ALLOWED_CATEGORIES = EnumSet.allOf(MerchantCategory.class);

    public List<ReceiptExtractionValidationError> validate(ReceiptExtractionResult result) {
        List<ReceiptExtractionValidationError> errors = new ArrayList<>();

        if (result == null) {
            errors.add(error("receipt", "extraction result is missing"));
            return errors;
        }

        if (result.vendor() == null || result.vendor().isBlank()) {
            errors.add(error("vendor", "merchant is required"));
        } else if (result.vendor().length() > MAX_VENDOR_LENGTH) {
            errors.add(error("vendor", "merchant exceeds " + MAX_VENDOR_LENGTH + " characters"));
        }

        if (result.receiptDate() == null) {
            errors.add(error("receiptDate", "receipt date is required"));
        } else {
            if (result.receiptDate().isAfter(LocalDate.now())) {
                errors.add(error("receiptDate", "receipt date is in the future"));
            }
            if (result.receiptDate().isBefore(LocalDate.of(1970, 1, 1))) {
                errors.add(error("receiptDate", "receipt date is implausibly old"));
            }
        }

        if (result.currency() == null || result.currency().isBlank()) {
            errors.add(error("currency", "currency is required"));
        } else if (!SUPPORTED_CURRENCIES.contains(result.currency().trim().toUpperCase(Locale.ROOT))) {
            errors.add(error("currency", "currency is not supported"));
        }

        if (result.merchantCategory() == null || !ALLOWED_CATEGORIES.contains(result.merchantCategory())) {
            errors.add(error("merchantCategory", "category is not allowlisted"));
        }

        validateAmount("subtotal", result.subtotal(), errors, false);
        validateAmount("tax", result.tax(), errors, false);
        validateAmount("tip", result.tip(), errors, false);
        validateAmount("total", result.total(), errors, true);

        if (result.total() != null && result.total().compareTo(MAX_TOTAL) > 0) {
            errors.add(error("total", "total exceeds sane limit"));
        }

        validateTotalMath(result, errors);
        validateLineItems(result, errors);

        if (errors.isEmpty()) {
            log.debug("Receipt extraction passed validation: vendor={} total={}", result.vendor(), result.total());
        } else {
            log.warn("Receipt extraction failed validation: {}", errors);
        }

        return errors;
    }

    private void validateAmount(
            String field,
            BigDecimal amount,
            List<ReceiptExtractionValidationError> errors,
            boolean required
    ) {
        if (amount == null) {
            if (required) {
                errors.add(error(field, field + " is required"));
            }
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(error(field, field + " must be non-negative"));
        }
    }

    private void validateTotalMath(ReceiptExtractionResult result, List<ReceiptExtractionValidationError> errors) {
        if (result.subtotal() == null || result.total() == null) {
            return;
        }

        BigDecimal tax = result.tax() == null ? BigDecimal.ZERO : result.tax();
        BigDecimal tip = result.tip() == null ? BigDecimal.ZERO : result.tip();
        BigDecimal computedTotal = result.subtotal().add(tax).add(tip);
        BigDecimal difference = computedTotal.subtract(result.total()).abs();

        if (difference.compareTo(AMOUNT_TOLERANCE) > 0) {
            errors.add(error("total", "subtotal + tax + tip does not approximately equal total"));
        }
    }

    private void validateLineItems(ReceiptExtractionResult result, List<ReceiptExtractionValidationError> errors) {
        if (result.lineItems() == null) {
            return;
        }

        for (int i = 0; i < result.lineItems().size(); i++) {
            ReceiptExtractionResult.LineItem item = result.lineItems().get(i);
            if (item.price() != null && item.price().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(error("lineItems[" + i + "].price", "line item price must be non-negative"));
            }
            if (item.quantity() != null && item.quantity() < 0) {
                errors.add(error("lineItems[" + i + "].quantity", "line item quantity must be non-negative"));
            }
        }
    }

    private ReceiptExtractionValidationError error(String field, String message) {
        return new ReceiptExtractionValidationError(field, message);
    }
}
