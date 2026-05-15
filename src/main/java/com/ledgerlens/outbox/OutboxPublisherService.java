package com.ledgerlens.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlens.receipt.ReceiptProcessingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${outbox.poller.batch-size:100}")
    private int batchSize;

    /**
     * Polls frequently for unprocessed outbox events.
     *
     * FOR UPDATE SKIP LOCKED in the query guarantees that in a multi-replica
     * deployment, two instances never process the same event. The transaction
     * wraps both the publish and the mark-processed update — if the broker
     * call throws, the row stays unprocessed and is retried on the next tick.
     *
     * This gives at-least-once delivery. The worker's duplicate-hash check
     * provides the idempotency guard on the consumer side.
     */
    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:250}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findUnprocessedForUpdate(batchSize);
        if (events.isEmpty()) return;

        for (OutboxEvent event : events) {
            try {
                ReceiptProcessingMessage message =
                        objectMapper.readValue(event.getPayload(), ReceiptProcessingMessage.class);
                rabbitTemplate.convertAndSend(exchange, routingKey, message);
                event.setProcessedAt(LocalDateTime.now());
                log.info("Outbox: published event={} aggregateId={}", event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Outbox: failed to publish event={} aggregateId={} — will retry",
                        event.getEventType(), event.getAggregateId(), e);
                throw new RuntimeException("Outbox publish failed — rolling back to preserve unprocessed state", e);
            }
        }
    }
}
