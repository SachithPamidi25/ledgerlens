package com.ledgerlens.receipt;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeVisionService {

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final ReceiptValidationService validationService;

    @Value("${anthropic.model:claude-haiku-4-5}")
    private String model;

    @Value("${claude.global-rate-limit-per-minute:50}")
    private int globalRateLimitPerMinute;

    /**
     * Resilience stack (outermost → innermost):
     *   Bulkhead       — max 5 concurrent calls, excess rejected immediately
     *   CircuitBreaker — opens after 50% failure rate over 10 calls
     *   Retry          — up to 3 attempts with exponential backoff (1s → 2s → 4s)
     *
     * OkHttp enforces a 40s hard call timeout underneath all of this.
     */
    @Bulkhead(name = "claude")
    @CircuitBreaker(name = "claude", fallbackMethod = "claudeFallback")
    @Retry(name = "claude")
    public ReceiptExtractionResult extractReceiptData(byte[] imageBytes) {
        enforceGlobalRateLimit();

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        Base64ImageSource.MediaType mediaType = detectMediaType(imageBytes);

        String prompt = """
                Analyze this receipt image and extract the following information.
                Respond ONLY with a valid JSON object, no other text.

                {
                    "vendor": "store name normalized",
                    "merchantCategory": "one of: FOOD, TRANSPORT, SHOPPING, ENTERTAINMENT, HEALTH, UTILITIES, OTHER",
                    "receiptDate": "YYYY-MM-DD or null",
                    "subtotal": numeric or null,
                    "tax": numeric or null,
                    "tip": numeric or null,
                    "total": numeric or null,
                    "currency": "INR or USD etc",
                    "lineItems": [
                        {"name": "item name", "quantity": 1, "price": 0.00}
                    ]
                }

                If you cannot read a field clearly, use null.
                Do not include any explanation, only the JSON.
                """;

        try {
            Message response = anthropicClient.messages().create(
                    MessageCreateParams.builder()
                            .model(model)
                            .maxTokens(1024)
                            .messages(List.of(
                                    MessageParam.builder()
                                            .role(MessageParam.Role.USER)
                                            .contentOfBlockParams(List.of(
                                                    ContentBlockParam.ofImage(
                                                            ImageBlockParam.builder()
                                                                    .source(Base64ImageSource.builder()
                                                                            .mediaType(mediaType)
                                                                            .data(base64Image)
                                                                            .build())
                                                                    .build()),
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
                    .orElseThrow(() -> new ClaudeNonRetryableException("No text content in Claude response"));

            log.debug("Claude raw response: {}", raw);

            String json = raw.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").strip();
            }

            ReceiptExtractionResult result;
            try {
                result = objectMapper.readValue(json, ReceiptExtractionResult.class);
            } catch (Exception e) {
                throw new ClaudeNonRetryableException("Claude returned unparseable JSON: " + json, e);
            }

            validationService.validate(result);
            return result;

        } catch (ClaudeExtractionException e) {
            throw e;
        } catch (Exception e) {
            if (isTimeout(e)) {
                log.warn("Claude API call timed out");
                throw new ClaudeTransientException("Claude API timed out", e);
            }
            log.error("Unexpected Claude API failure", e);
            throw new ClaudeTransientException("Claude API call failed", e);
        }
    }

    /**
     * Called by Resilience4j when the circuit breaker is OPEN.
     * Must have the same signature as extractReceiptData + a Throwable param.
     */
    @SuppressWarnings("unused")
    private ReceiptExtractionResult claudeFallback(byte[] imageBytes, Throwable t) {
        ClaudeExtractionException extractionException = findCause(t, ClaudeExtractionException.class);
        if (extractionException != null) {
            throw extractionException;
        }
        if (!(t instanceof CallNotPermittedException)) {
            throw new ClaudeTransientException("Claude API call failed", t);
        }

        log.error("Claude circuit breaker is OPEN — failing fast. Cause: {}", t.getMessage());
        throw new ClaudeCircuitOpenException(
                "Claude AI is temporarily unavailable. Please try again in 30 seconds.", t);
    }

    /**
     * Global rate limit across all users — prevents exceeding Anthropic's API quota.
     * Fixed window keyed by current minute. Transient exception allows the Retry to
     * back off and retry after a brief wait.
     */
    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private void enforceGlobalRateLimit() {
        String minuteKey = "claude:global:" + (System.currentTimeMillis() / 60_000);
        Long count = redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, 2, TimeUnit.MINUTES);
        if (count != null && count > globalRateLimitPerMinute) {
            throw new ClaudeTransientException("Global Claude rate limit exceeded (" + count + "/" + globalRateLimitPerMinute + " req/min)");
        }
    }

    /**
     * Detects image format from magic bytes so we don't lie to the Claude API about content type.
     * Falls back to JPEG for unknown formats since it is the most common receipt scan format.
     */
    private Base64ImageSource.MediaType detectMediaType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return Base64ImageSource.MediaType.IMAGE_JPEG;
        }
        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return Base64ImageSource.MediaType.IMAGE_PNG;
        }
        if (bytes.length >= 3 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return Base64ImageSource.MediaType.IMAGE_GIF;
        }
        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x52 && (bytes[1] & 0xFF) == 0x49
                && (bytes[2] & 0xFF) == 0x46 && (bytes[3] & 0xFF) == 0x46) {
            return Base64ImageSource.MediaType.IMAGE_WEBP;
        }
        log.warn("Unknown image format — falling back to JPEG");
        return Base64ImageSource.MediaType.IMAGE_JPEG;
    }

    private boolean isTimeout(Throwable t) {
        if (t instanceof SocketTimeoutException) return true;
        Throwable cause = t.getCause();
        return cause instanceof SocketTimeoutException;
    }
}
