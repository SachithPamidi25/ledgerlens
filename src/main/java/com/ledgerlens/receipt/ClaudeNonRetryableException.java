package com.ledgerlens.receipt;

/**
 * Permanent failure — must NOT be retried.
 * Examples: unreadable image, malformed JSON from Claude, auth error.
 * Retrying would waste API credits and time.
 */
public class ClaudeNonRetryableException extends ClaudeExtractionException {
    public ClaudeNonRetryableException(String message) { super(message); }
    public ClaudeNonRetryableException(String message, Throwable cause) { super(message, cause); }
}
