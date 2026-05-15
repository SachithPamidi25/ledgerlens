package com.ledgerlens.receipt;

import com.ledgerlens.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    /**
     * @param idempotencyKey optional — if provided, replays return the same response
     *                       without creating a duplicate receipt. Clients should send
     *                       a UUID they generate per upload intent.
     */
    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> getUploadUrl(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String filename,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        UploadUrlResponse response = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? receiptService.createUpload(principal.userId(), filename, idempotencyKey)
                : receiptService.createUpload(principal.userId(), filename);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<?> triggerProcessing(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        receiptService.triggerProcessing(id, principal.userId());
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<?> getReceipts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(receiptService.listReceipts(principal.userId(), pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReceipt(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        receiptService.deleteReceipt(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<?> deleteLedger(@AuthenticationPrincipal UserPrincipal principal) {
        int deleted = receiptService.deleteLedger(principal.userId());
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
