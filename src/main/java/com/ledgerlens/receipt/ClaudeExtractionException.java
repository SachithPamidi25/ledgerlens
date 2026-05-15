package com.ledgerlens.receipt;

/**
 * Base exception for all Claude extraction failures.
 *
 * Subclass hierarchy drives retry and circuit breaker behaviour:
 *   ClaudeTransientException    → should be retried (network blip, timeout, rate limit)
 *   ClaudeNonRetryableException → must NOT be retried (bad image, auth error, JSON parse)
 *   ClaudeCircuitOpenException  → circuit is open; caller should fail fast
 */
public class ClaudeExtractionException extends RuntimeException {
    public ClaudeExtractionException(String message) { super(message); }
    public ClaudeExtractionException(String message, Throwable cause) { super(message, cause); }
}
