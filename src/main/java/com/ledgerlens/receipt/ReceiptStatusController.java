package com.ledgerlens.receipt;

import com.ledgerlens.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptStatusController {

    private final RedisMessageListenerContainer listenerContainer;
    private final ReceiptService receiptService;

    /**
     * Opens a Server-Sent Events stream for a single receipt's processing status.
     *
     * Flow:
     *   1. Client POSTs /upload-url → gets receiptId
     *   2. Client PUTs file to MinIO presigned URL
     *   3. Client POSTs /{id}/process
     *   4. Client opens GET /{id}/status-stream — this endpoint
     *   5. When worker finishes, ReceiptPersistenceService publishes to Redis pub/sub
     *   6. The listener here forwards the event to the SSE stream and closes it
     *
     * If the stream times out (5 min), the client falls back to polling GET /{id}.
     * No message is lost — the DB is authoritative, pub/sub is just the push notification.
     */
    @GetMapping(value = "/{id}/status-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        // Verify the receipt belongs to this user before opening the stream
        receiptService.getReceipt(id, principal.userId());

        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout
        String channel = "receipt.status." + id;

        MessageListener listener = (message, pattern) -> {
            try {
                String payload = new String(message.getBody());
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(payload));
                if (isTerminalStatus(payload)) {
                    emitter.complete();
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        };

        ChannelTopic topic = new ChannelTopic(channel);
        listenerContainer.addMessageListener(listener, topic);

        Runnable cleanup = () -> listenerContainer.removeMessageListener(listener, topic);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.info("SSE stream opened: receiptId={} userId={}", id, principal.userId());
        return emitter;
    }

    private boolean isTerminalStatus(String payload) {
        return payload.contains("\"COMPLETED\"")
                || payload.contains("\"FAILED\"")
                || payload.contains("\"DUPLICATE\"")
                || payload.contains("\"PERMANENTLY_FAILED\"");
    }
}
