package com.ledgerlens.insights;

import com.ledgerlens.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;
    private final GroundedSpendingQaService groundedSpendingQaService;

    @GetMapping
    public ResponseEntity<?> getInsights(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "3") int months) {

        if (months < 1 || months > 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "months must be between 1 and 6"));
        }

        InsightsResponse response = insightsService.generateInsights(principal.userId(), months);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ask")
    public ResponseEntity<?> askSpendingQuestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int limit) {

        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question is required"));
        }
        if (limit < 1 || limit > 10) {
            return ResponseEntity.badRequest().body(Map.of("error", "limit must be between 1 and 10"));
        }

        return ResponseEntity.ok(groundedSpendingQaService.answerQuestion(principal.userId(), question, limit));
    }
}
