package com.ledgerlens.receipt.extraction;

import com.ledgerlens.receipt.MerchantCategory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Profile("mock-ai")
public class MockExtractionClient implements AiExtractionClient {

    @Override
    public ReceiptExtractionResult extractReceiptData(byte[] imageBytes) {
        return new ReceiptExtractionResult(
                "Mock Merchant",
                MerchantCategory.OTHER,
                LocalDate.now(),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                "INR",
                List.of()
        );
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String modelName() {
        return "mock-receipt-extractor";
    }
}
