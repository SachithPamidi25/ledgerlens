package com.ledgerlens.receipt.eval;

final class ReceiptExtractionEvalReportWriter {

    private ReceiptExtractionEvalReportWriter() {
    }

    static String toMarkdown(ReceiptExtractionEvalMetrics metrics) {
        return """
                # AI Extraction Evaluation

                Dataset size: %d receipts
                Merchant accuracy: %s%%
                Total amount accuracy: %s%%
                Date accuracy: %s%%
                Category accuracy: %s%%
                JSON validity rate: %s%%
                Validation failure rate: %s%%
                Avg extraction latency: %sms
                Avg cost per receipt: INR %s / USD %s

                Notes:
                - This report is generated from the offline eval harness in `src/test/java/com/ledgerlens/receipt/eval`.
                - Demo receipts live in `src/test/resources/eval/receipts`.
                - Golden outputs live in `src/test/resources/eval/expected_outputs`.
                - The deterministic eval client keeps CI free of live AI API calls; the scoring layer can be reused with real providers.
                """.formatted(
                metrics.datasetSize(),
                metrics.merchantAccuracy(),
                metrics.amountAccuracy(),
                metrics.dateAccuracy(),
                metrics.categoryAccuracy(),
                metrics.jsonValidityRate(),
                metrics.validationFailureRate(),
                metrics.averageLatencyMs(),
                metrics.averageCostInr(),
                metrics.averageCostUsd()
        );
    }
}
