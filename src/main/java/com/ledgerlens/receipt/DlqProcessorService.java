package com.ledgerlens.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqProcessorService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ReceiptProcessingService receiptProcessingService;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${dlq.max-retries:4}")
    private int maxRetries;

    private static final long BASE_DELAY_MS = 5_000;   // 5s
    private static final long MAX_DELAY_MS  = 120_000; // 2 min

    /**
     * Consumes messages from the Dead Letter Queue.
     *
     * Strategy — exponential backoff republish:
     *   Attempt 1 → wait  5s  → republish to main queue
     *   Attempt 2 → wait 10s  → republish
     *   Attempt 3 → wait 20s  → republish
     *   Attempt 4 → wait 40s  → republish
     *   Attempt 5 → PERMANENTLY_FAILED — mark receipt, log alert, drop message
     *
     * The retry count is tracked via a custom header (x-retry-count) that we
     * increment on every republish. RabbitMQ's x-death header is also present
     * but its count semantics vary across broker versions.
     *
     * Thread.sleep is intentional here — the DLQ consumer is a low-throughput
     * error-recovery path. Blocking the consumer thread for backoff is acceptable
     * and avoids the complexity of a scheduled retry queue.
     */
    @RabbitListener(queues = "${rabbitmq.queue.receipt}.dead")
    public void processDlqMessage(Message amqpMessage) {
        int retryCount = getRetryCount(amqpMessage);
        ReceiptProcessingMessage processingMessage = deserialize(amqpMessage);

        if (processingMessage == null) {
            log.error("DLQ: unparseable message — dropping permanently. Body: {}",
                    new String(amqpMessage.getBody()));
            return;
        }

        UUID receiptId = processingMessage.receiptId();

        if (retryCount >= maxRetries) {
            log.error("DLQ: max retries ({}) exhausted for receiptId={} — marking PERMANENTLY_FAILED",
                    maxRetries, receiptId);
            receiptProcessingService.markPermanentlyFailed(receiptId);
            return;
        }

        long delayMs = Math.min(BASE_DELAY_MS * (long) Math.pow(2, retryCount), MAX_DELAY_MS);
        log.warn("DLQ: retry {}/{} for receiptId={} — waiting {}ms before republishing",
                retryCount + 1, maxRetries, receiptId, delayMs);

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("DLQ: interrupted during backoff for receiptId={}", receiptId);
            return;
        }

        amqpMessage.getMessageProperties().setHeader("x-retry-count", retryCount + 1);
        rabbitTemplate.send(exchange, routingKey, amqpMessage);
        log.info("DLQ: republished receiptId={} (attempt {})", receiptId, retryCount + 1);
    }

    private int getRetryCount(Message message) {
        Object header = message.getMessageProperties().getHeaders().get("x-retry-count");
        if (header instanceof Integer count) return count;
        if (header instanceof Long count) return count.intValue();
        return 0;
    }

    private ReceiptProcessingMessage deserialize(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), ReceiptProcessingMessage.class);
        } catch (Exception e) {
            log.error("DLQ: failed to deserialize message", e);
            return null;
        }
    }
}
