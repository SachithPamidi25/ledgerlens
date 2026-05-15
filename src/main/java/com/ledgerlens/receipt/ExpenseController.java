package com.ledgerlens.receipt;

import com.ledgerlens.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ReceiptService receiptService;

    @GetMapping("/summary")
    public ResponseEntity<?> getMonthlySummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        if (year != null && (year < 1900 || year > 2200)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid year"));
        }
        if (month != null && (month < 1 || month > 12)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid month"));
        }
        if ((year == null) != (month == null)) {
            return ResponseEntity.badRequest().body(Map.of("error", "year and month must be provided together"));
        }

        return ResponseEntity.ok(expenseService.getMonthlySummary(principal.userId(), year, month));
    }

    @GetMapping("/receipts/{id}")
    public ResponseEntity<?> getReceipt(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(receiptService.getReceipt(id, principal.userId()));
    }
}
