package com.ledgerlens.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 60;

    // Atomic: INCR and EXPIRE in a single round-trip — no gap where key exists without TTL
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
            "local count = redis.call('INCR', KEYS[1])\n" +
            "if count == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return count",
            Long.class
    );

    public boolean tryConsume(String key) {
        Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of("ratelimit:" + key),
                String.valueOf(WINDOW_SECONDS)
        );
        return count != null && count <= MAX_ATTEMPTS;
    }
}
