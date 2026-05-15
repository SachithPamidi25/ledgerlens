package com.ledgerlens.receipt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptProcessor {

    private final ReceiptProcessingService receiptProcessingService;

    @RabbitListener(queues = "${rabbitmq.queue.receipt}")
    public void processReceipt(ReceiptProcessingMessage message) {
        MDC.put("traceId", message.traceId());
        MDC.put("receiptId", message.receiptId().toString());
        MDC.put("userId", message.userId().toString());
        try {
            log.info("Processing receipt started");
            ReceiptStatus result = receiptProcessingService.process(message);
            log.info("Processing receipt finished with status: {}", result);
        } catch (DataIntegrityViolationException e) {
            if (isContentHashConstraint(e)) {
                log.info("Duplicate receipt detected via DB constraint");
                receiptProcessingService.markDuplicate(message.receiptId(), null);
            } else {
                log.error("Unexpected constraint violation", e);
                receiptProcessingService.markFailed(message.receiptId());
                throw new AmqpRejectAndDontRequeueException("Constraint violation during receipt processing", e);
            }
        } catch (ClaudeNonRetryableException e) {
            log.warn("Receipt processing failed permanently: {}", e.getMessage());
            receiptProcessingService.markPermanentlyFailed(message.receiptId());
        } catch (Exception e) {
            log.error("Failed to process receipt", e);
            receiptProcessingService.markFailed(message.receiptId());
            throw new AmqpRejectAndDontRequeueException("Receipt processing failed, routing to DLQ", e);
        } finally {
            MDC.clear();
        }
    }

    private boolean isContentHashConstraint(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        return msg != null && msg.contains("uq_receipts_content_hash_user");
    }
}
