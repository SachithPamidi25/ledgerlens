package com.ledgerlens.receipt;

/** Thrown by the circuit breaker fallback when the circuit is OPEN. */
public class ClaudeCircuitOpenException extends ClaudeExtractionException {
    public ClaudeCircuitOpenException(String message, Throwable cause) { super(message, cause); }
}
