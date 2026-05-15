package com.ledgerlens.insights;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlens.receipt.ClaudeCircuitOpenException;
import com.ledgerlens.receipt.ClaudeNonRetryableException;
import com.ledgerlens.receipt.ClaudeTransientException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeInsightsService {

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${anthropic.model:claude-haiku-4-5}")
    private String model;

    @Value("${claude.global-rate-limit-per-minute:50}")
    private int globalRateLimitPerMinute;

    @Bulkhead(name = "insights")
    @CircuitBreaker(name = "insights", fallbackMethod = "insightsFallback")
    @Retry(name = "insights")
    public List<Insight> generateInsights(String prompt) {
        enforceGlobalRateLimit();

        try {
            Message response = anthropicClient.messages().create(
                    MessageCreateParams.builder()
                            .model(model)
                            .maxTokens(1024)
                            .messages(List.of(
                                    MessageParam.builder()
                                            .role(MessageParam.Role.USER)
                                            .contentOfBlockParams(List.of(
                                                    ContentBlockParam.ofText(
                                                            TextBlockParam.builder()
                                                                    .text(prompt)
                                                                    .build())))
                                            .build()))
                            .build());

            String raw = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .findFirst()
                    .orElseThrow(() -> new ClaudeNonRetryableException("No text in Claude insights response"));

            String json = raw.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").strip();
            }

            try {
                return objectMapper.readValue(json, new TypeReference<List<Insight>>() {});
            } catch (Exception e) {
                throw new ClaudeNonRetryableException("Claude returned unparseable insights JSON: " + json, e);
            }

        } catch (ClaudeNonRetryableException | ClaudeCircuitOpenException e) {
            throw e;
        } catch (Exception e) {
            if (isTimeout(e)) {
                log.warn("Claude insights API call timed out");
                throw new ClaudeTransientException("Claude insights API timed out", e);
            }
            log.error("Unexpected Claude insights API failure", e);
            throw new ClaudeTransientException("Claude insights API call failed", e);
        }
    }

    @SuppressWarnings("unused")
    private List<Insight> insightsFallback(String prompt, Throwable t) {
        log.error("Insights circuit breaker is OPEN — failing fast. Cause: {}", t.getMessage());
        throw new ClaudeCircuitOpenException(
                "AI insights are temporarily unavailable. Please try again in 30 seconds.", t);
    }

    private void enforceGlobalRateLimit() {
        String minuteKey = "claude:global:" + (System.currentTimeMillis() / 60_000);
        Long count = redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, 2, TimeUnit.MINUTES);
        if (count != null && count > globalRateLimitPerMinute) {
            throw new ClaudeTransientException("Global Claude rate limit exceeded (" + count + "/" + globalRateLimitPerMinute + " req/min)");
        }
    }

    private boolean isTimeout(Throwable t) {
        if (t instanceof SocketTimeoutException) return true;
        Throwable cause = t.getCause();
        return cause instanceof SocketTimeoutException;
    }
}
