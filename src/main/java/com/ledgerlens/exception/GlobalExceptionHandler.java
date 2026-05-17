package com.ledgerlens.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<?> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleConflict(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(409).body(Map.of("error", "Resource already exists"));
    }

    @ExceptionHandler({
            AsyncRequestNotUsableException.class,
            AsyncRequestTimeoutException.class,
            IOException.class
    })
    public Object handleClientDisconnect(Exception ex, HttpServletRequest request) {
        if (isClientDisconnect(ex) || isSseRequest(request)) {
            log.debug("Client disconnected before async response completed: {}", ex.getMessage());
            return ResponseEntity.noContent().build();
        }

        log.error("Unhandled I/O exception", ex);
        return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String contentType = request.getContentType();
        return uri.endsWith("/status-stream")
                || MediaType.TEXT_EVENT_STREAM_VALUE.equals(contentType)
                || (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    private boolean isClientDisconnect(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.contains("ClientAbortException")
                    || className.contains("AsyncRequestNotUsableException")
                    || className.contains("AsyncRequestTimeoutException")
                    || (message != null && message.contains("An established connection was aborted"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
