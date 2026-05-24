package com.ledgerlens.receipt.extraction;

/** Thrown by the circuit breaker fallback when the circuit is OPEN. */
public class ClaudeCircuitOpenException extends ClaudeExtractionException {
    public ClaudeCircuitOpenException(String message, Throwable cause) { super(message, cause); }
}
