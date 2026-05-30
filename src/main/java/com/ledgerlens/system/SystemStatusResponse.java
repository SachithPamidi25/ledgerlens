package com.ledgerlens.system;

import java.time.Instant;
import java.util.List;

public record SystemStatusResponse(
        String application,
        String status,
        Instant startedAt,
        long uptimeSeconds,
        String javaVersion,
        List<String> activeProfiles
) {
}
