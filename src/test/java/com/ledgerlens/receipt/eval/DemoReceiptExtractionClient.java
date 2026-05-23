package com.ledgerlens.receipt.eval;

import com.ledgerlens.receipt.AiExtractionClient;
import com.ledgerlens.receipt.MerchantCategory;
import com.ledgerlens.receipt.ReceiptExtractionResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

final class DemoReceiptExtractionClient implements AiExtractionClient {

    @Override
    public ReceiptExtractionResult extractReceiptData(byte[] imageBytes) {
        String text = new String(imageBytes, StandardCharsets.UTF_8);
        return new ReceiptExtractionResult(
                value(text, "merchant"),
                MerchantCategory.valueOf(value(text, "category")),
                LocalDate.parse(value(text, "date")),
                decimal(text, "subtotal"),
                decimal(text, "tax"),
                decimal(text, "tip"),
                decimal(text, "total"),
                value(text, "currency"),
                List.of()
        );
    }

    private String value(String text, String key) {
        String prefix = key + ":";
        return text.lines()
                .filter(line -> line.toLowerCase().startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing eval field: " + key));
    }

    private BigDecimal decimal(String text, String key) {
        return new BigDecimal(value(text, key));
    }
}
