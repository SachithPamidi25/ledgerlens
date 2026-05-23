package com.ledgerlens.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiToolCallAuditService {

    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String toolName, Map<String, Object> arguments, String resultStatus, long latencyMs) {
        try {
            entityManager.createNativeQuery("""
                            INSERT INTO ai_tool_call_audit (user_id, tool_name, arguments, result_status, latency_ms)
                            VALUES (:userId, :toolName, CAST(:arguments AS jsonb), :resultStatus, :latencyMs)
                            """)
                    .setParameter("userId", userId)
                    .setParameter("toolName", toolName)
                    .setParameter("arguments", objectMapper.writeValueAsString(arguments))
                    .setParameter("resultStatus", resultStatus)
                    .setParameter("latencyMs", latencyMs)
                    .executeUpdate();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize AI tool call arguments", e);
        } catch (RuntimeException e) {
            log.warn("Failed to audit AI tool call: userId={} toolName={}", userId, toolName, e);
        }
    }
}
