package com.ledgerlens.receipt;

/** Transient failure — safe to retry. Network errors, timeouts, 429s. */
public class ClaudeTransientException extends ClaudeExtractionException {
    public ClaudeTransientException(String message) { super(message); }
    public ClaudeTransientException(String message, Throwable cause) { super(message, cause); }
}
