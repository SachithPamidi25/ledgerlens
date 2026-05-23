package com.ledgerlens.agent;

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
@RequestMapping("/api/agent/finance")
@RequiredArgsConstructor
public class FinanceAgentController {

    private final ReadOnlyFinanceAgentService financeAgentService;

    @GetMapping
    public ResponseEntity<?> ask(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String question) {

        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question is required"));
        }

        return ResponseEntity.ok(financeAgentService.answer(principal.userId(), question));
    }
}
