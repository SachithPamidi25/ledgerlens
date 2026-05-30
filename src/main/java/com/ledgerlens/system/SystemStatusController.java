package com.ledgerlens.system;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemStatusController {

    private final Environment environment;

    @Value("${spring.application.name:ledgerlens}")
    private String applicationName = "ledgerlens";

    @GetMapping("/status")
    public SystemStatusResponse status() {
        Instant startedAt = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.isEmpty()) {
            profiles = List.of("default");
        }

        return new SystemStatusResponse(
                applicationName,
                "UP",
                startedAt,
                Duration.between(startedAt, Instant.now()).toSeconds(),
                Runtime.version().toString(),
                profiles
        );
    }
}
