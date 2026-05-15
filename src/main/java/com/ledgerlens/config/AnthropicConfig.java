package com.ledgerlens.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AnthropicConfig {

    @Bean
    public AnthropicClient anthropicClient(@Value("${anthropic.api-key}") String apiKey) {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                // Hard cap on a single API call — OkHttp cancels after 40s.
                // Resilience4j circuit breaker also flags calls > 15s as "slow"
                // so the breaker opens before the timeout fires in sustained degradation.
                .timeout(Duration.ofSeconds(40))
                // Disable SDK-level retries — Resilience4j @Retry owns retry logic
                // so we don't get double-retrying with uncoordinated backoff.
                .maxRetries(0)
                .build();
    }
}
