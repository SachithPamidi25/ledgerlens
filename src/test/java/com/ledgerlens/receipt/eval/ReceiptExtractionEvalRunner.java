package com.ledgerlens.receipt.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlens.receipt.AiExtractionClient;
import com.ledgerlens.receipt.ReceiptExtractionResult;
import com.ledgerlens.receipt.ReceiptExtractionValidator;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ReceiptExtractionEvalRunner {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.05");

    private final AiExtractionClient extractionClient;
    private final ReceiptExtractionValidator validator;
    private final ObjectMapper objectMapper;
    private final BigDecimal estimatedCostPerReceiptUsd;
    private final Long fixedLatencyMsPerReceipt;

    ReceiptExtractionEvalRunner(
            AiExtractionClient extractionClient,
            ReceiptExtractionValidator validator,
            ObjectMapper objectMapper,
            BigDecimal estimatedCostPerReceiptUsd
    ) {
        this(extractionClient, validator, objectMapper, estimatedCostPerReceiptUsd, null);
    }

    ReceiptExtractionEvalRunner(
            AiExtractionClient extractionClient,
            ReceiptExtractionValidator validator,
            ObjectMapper objectMapper,
            BigDecimal estimatedCostPerReceiptUsd,
            Long fixedLatencyMsPerReceipt
    ) {
        this.extractionClient = extractionClient;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.estimatedCostPerReceiptUsd = estimatedCostPerReceiptUsd;
        this.fixedLatencyMsPerReceipt = fixedLatencyMsPerReceipt;
    }

    ReceiptExtractionEvalMetrics run(Path receiptDirectory, Path expectedOutputDirectory) throws Exception {
        List<Path> receiptPaths;
        try (var stream = Files.list(receiptDirectory)) {
            receiptPaths = stream
                    .filter(path -> path.getFileName().toString().endsWith(".receipt.txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        int validJsonCount = 0;
        int validationFailureCount = 0;
        int merchantMatches = 0;
        int dateMatches = 0;
        int amountMatches = 0;
        int categoryMatches = 0;
        long totalLatencyMs = 0;

        for (Path receiptPath : receiptPaths) {
            String id = receiptPath.getFileName().toString().replace(".receipt.txt", "");
            ReceiptExtractionResult expected = objectMapper.readValue(
                    expectedOutputDirectory.resolve(id + ".json").toFile(),
                    ReceiptExtractionResult.class
            );
            ReceiptExtractionEvalCase evalCase = new ReceiptExtractionEvalCase(id, receiptPath, expected);

            long startNanos = System.nanoTime();
            ReceiptExtractionResult actual = extractionClient.extractReceiptData(Files.readAllBytes(evalCase.receiptPath()));
            totalLatencyMs += fixedLatencyMsPerReceipt != null
                    ? fixedLatencyMsPerReceipt
                    : Math.max(1, (System.nanoTime() - startNanos) / 1_000_000);

            validJsonCount++;
            if (!validator.validate(actual).isEmpty()) {
                validationFailureCount++;
            }
            if (normalize(actual.vendor()).equals(normalize(evalCase.expected().vendor()))) {
                merchantMatches++;
            }
            if (actual.receiptDate() != null && actual.receiptDate().equals(evalCase.expected().receiptDate())) {
                dateMatches++;
            }
            if (actual.total() != null
                    && actual.total().subtract(evalCase.expected().total()).abs().compareTo(AMOUNT_TOLERANCE) <= 0) {
                amountMatches++;
            }
            if (actual.merchantCategory() == evalCase.expected().merchantCategory()) {
                categoryMatches++;
            }
        }

        return new ReceiptExtractionEvalMetrics(
                receiptPaths.size(),
                validJsonCount,
                validationFailureCount,
                merchantMatches,
                dateMatches,
                amountMatches,
                categoryMatches,
                totalLatencyMs,
                estimatedCostPerReceiptUsd.multiply(BigDecimal.valueOf(receiptPaths.size()))
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
