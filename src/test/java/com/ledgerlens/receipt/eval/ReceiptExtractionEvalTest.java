package com.ledgerlens.receipt.eval;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ledgerlens.receipt.ReceiptExtractionValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptExtractionEvalTest {

    @Test
    void generateOfflineEvalReport() throws Exception {
        Path evalRoot = Path.of("src/test/resources/eval");
        ReceiptExtractionEvalRunner runner = new ReceiptExtractionEvalRunner(
                new DemoReceiptExtractionClient(),
                new ReceiptExtractionValidator(),
                JsonMapper.builder().addModule(new JavaTimeModule()).build(),
                new BigDecimal("0.0025"),
                1L
        );

        ReceiptExtractionEvalMetrics metrics = runner.run(
                evalRoot.resolve("receipts"),
                evalRoot.resolve("expected_outputs")
        );
        String report = ReceiptExtractionEvalReportWriter.toMarkdown(metrics);

        Files.writeString(Path.of("eval-report.md"), report);

        assertThat(metrics.datasetSize()).isGreaterThanOrEqualTo(5);
        assertThat(metrics.jsonValidityRate()).isEqualByComparingTo("100.0");
        assertThat(metrics.validationFailureRate()).isEqualByComparingTo("0.0");
        assertThat(report).contains("AI Extraction Evaluation");
    }
}
