# AI Extraction Evaluation

Dataset size: 10 receipts
Merchant accuracy: 100.0%
Total amount accuracy: 100.0%
Date accuracy: 100.0%
Category accuracy: 100.0%
JSON validity rate: 100.0%
Validation failure rate: 0.0%
Avg extraction latency: 1.0ms
Avg cost per receipt: INR 0.21 / USD 0.0025

Notes:
- This report is generated from the offline eval harness in `src/test/java/com/ledgerlens/receipt/eval`.
- Demo receipts live in `src/test/resources/eval/receipts`.
- Golden outputs live in `src/test/resources/eval/expected_outputs`.
- The deterministic eval client keeps CI free of live AI API calls; the scoring layer can be reused with real providers.
