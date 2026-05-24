package com.ledgerlens.receipt.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AiObservabilityService {

    private final MeterRegistry meterRegistry;

    @Value("${ai.extraction.estimated-cost-usd:0.0025}")
    private BigDecimal estimatedCostUsd;

    public void recordExtractionLatency(long latencyMs, String provider, String model) {
        DistributionSummary.builder("ai_extraction_latency_ms")
                .description("AI receipt extraction latency in milliseconds")
                .tag("provider", provider)
                .tag("model", model)
                .register(meterRegistry)
                .record(latencyMs);
    }

    public void recordExtractionSuccess(String provider, String model) {
        counter("ai_extraction_success_total", provider, model).increment();
        DistributionSummary.builder("ai_cost_per_receipt")
                .description("Estimated AI extraction cost per receipt in USD")
                .tag("provider", provider)
                .tag("model", model)
                .register(meterRegistry)
                .record(estimatedCostUsd.doubleValue());
    }

    public void recordExtractionFailure(String provider, String model, String reason) {
        Counter.builder("ai_extraction_failure_total")
                .description("AI receipt extraction failures")
                .tag("provider", provider)
                .tag("model", model)
                .tag("reason", sanitizeTag(reason))
                .register(meterRegistry)
                .increment();
    }

    public void recordSchemaValidationFailure(String provider, String model) {
        counter("ai_schema_validation_failure_total", provider, model).increment();
    }

    public void recordPromptInjectionDetected(String provider, String model) {
        counter("ai_prompt_injection_detected_total", provider, model).increment();
    }

    public void recordRetry(String provider, String model) {
        counter("ai_retry_count", provider, model).increment();
    }

    public void recordCacheHit(String provider, String model) {
        counter("ai_cache_hit_ratio", provider, model).increment();
    }

    public void recordProviderTimeout(String provider, String model) {
        counter("ai_provider_timeout_total", provider, model).increment();
    }

    private Counter counter(String name, String provider, String model) {
        return Counter.builder(name)
                .tag("provider", provider)
                .tag("model", model)
                .register(meterRegistry);
    }

    private String sanitizeTag(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        return reason.toLowerCase()
                .replaceAll("[^a-z0-9_ -]", "")
                .replaceAll("\\s+", "_");
    }
}
