package com.ledgerlens.receipt;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiObservabilityServiceTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AiObservabilityService observabilityService = new AiObservabilityService(meterRegistry);

    @Test
    void recordsExtractionMetricsWithProviderAndModelTags() {
        ReflectionTestUtils.setField(observabilityService, "estimatedCostUsd", new BigDecimal("0.0025"));

        observabilityService.recordExtractionLatency(125, "anthropic", "claude-haiku-4-5");
        observabilityService.recordExtractionSuccess("anthropic", "claude-haiku-4-5");
        observabilityService.recordExtractionFailure("anthropic", "claude-haiku-4-5", "SocketTimeoutException");
        observabilityService.recordSchemaValidationFailure("anthropic", "claude-haiku-4-5");
        observabilityService.recordPromptInjectionDetected("anthropic", "claude-haiku-4-5");
        observabilityService.recordProviderTimeout("anthropic", "claude-haiku-4-5");

        assertThat(meterRegistry.find("ai_extraction_latency_ms").summary().count()).isEqualTo(1);
        assertThat(meterRegistry.find("ai_extraction_success_total").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("ai_extraction_failure_total").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("ai_schema_validation_failure_total").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("ai_prompt_injection_detected_total").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("ai_provider_timeout_total").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("ai_cost_per_receipt").summary().totalAmount()).isEqualTo(0.0025);
    }
}
