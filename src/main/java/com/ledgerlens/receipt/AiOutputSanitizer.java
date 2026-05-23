package com.ledgerlens.receipt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AiOutputSanitizer {

    private final AiSecurityPolicy securityPolicy;

    public SanitizedExtraction sanitize(ReceiptExtractionResult result) {
        if (result == null) {
            return new SanitizedExtraction(null, List.of(
                    new ReceiptExtractionValidationError("receipt", "AI extraction result is missing")
            ));
        }

        List<ReceiptExtractionValidationError> securityErrors = new ArrayList<>();
        String vendor = sanitizeText(result.vendor(), "vendor", securityErrors);
        String currency = sanitizeCurrency(result.currency());
        List<ReceiptExtractionResult.LineItem> lineItems = sanitizeLineItems(result.lineItems(), securityErrors);

        ReceiptExtractionResult sanitized = new ReceiptExtractionResult(
                vendor,
                result.merchantCategory(),
                result.receiptDate(),
                result.subtotal(),
                result.tax(),
                result.tip(),
                result.total(),
                currency,
                lineItems
        );

        return new SanitizedExtraction(sanitized, securityErrors);
    }

    private List<ReceiptExtractionResult.LineItem> sanitizeLineItems(
            List<ReceiptExtractionResult.LineItem> lineItems,
            List<ReceiptExtractionValidationError> securityErrors
    ) {
        if (lineItems == null) {
            return null;
        }

        List<ReceiptExtractionResult.LineItem> sanitized = new ArrayList<>();
        for (int i = 0; i < lineItems.size(); i++) {
            ReceiptExtractionResult.LineItem item = lineItems.get(i);
            sanitized.add(new ReceiptExtractionResult.LineItem(
                    sanitizeText(item.name(), "lineItems[" + i + "].name", securityErrors),
                    item.quantity(),
                    item.price()
            ));
        }
        return sanitized;
    }

    private String sanitizeText(
            String value,
            String field,
            List<ReceiptExtractionValidationError> securityErrors
    ) {
        if (value == null) {
            return null;
        }

        String sanitized = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (securityPolicy.containsPromptInjectionSignal(sanitized)) {
            securityErrors.add(new ReceiptExtractionValidationError(
                    field,
                    "field contains prompt-injection or tool-execution text"
            ));
        }
        return sanitized;
    }

    private String sanitizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    public record SanitizedExtraction(
            ReceiptExtractionResult result,
            List<ReceiptExtractionValidationError> securityErrors
    ) {
    }
}
