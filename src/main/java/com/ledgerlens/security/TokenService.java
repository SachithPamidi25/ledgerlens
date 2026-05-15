package com.ledgerlens.security;

import com.ledgerlens.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String USER_INVALIDATED_PREFIX = "user_invalidated:";
    private static final RedisScript<String> GET_AND_DELETE_SCRIPT = RedisScript.of(
            "local value = redis.call('GET', KEYS[1])\n" +
            "if value then\n" +
            "  redis.call('DEL', KEYS[1])\n" +
            "end\n" +
            "return value",
            String.class
    );

    // Keyed by jti — supports multiple devices, each session has its own slot
    public void storeRefreshToken(String jti, String email) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + jti,
                email,
                jwtProperties.getRefreshExpiration(), TimeUnit.MILLISECONDS
        );
    }

    // Atomic getAndDelete — no race condition, no double-spend
    public boolean validateAndDeleteRefreshToken(String jti, String email) {
        String stored = redisTemplate.execute(
                GET_AND_DELETE_SCRIPT,
                List.of(REFRESH_TOKEN_PREFIX + jti)
        );
        return email.equals(stored);
    }

    public void deleteRefreshToken(String jti) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + jti);
    }

    public void blacklistToken(String token, long remainingMillis) {
        if (remainingMillis > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token,
                    "blacklisted",
                    remainingMillis, TimeUnit.MILLISECONDS
            );
        }
    }

    public boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(BLACKLIST_PREFIX + token)
        );
    }

    /**
     * Marks all sessions for a user as invalid for the duration of one access-token TTL.
     * Called on refresh-token reuse detection so that any access token issued to a potential
     * attacker also stops working within the same window.
     */
    public void invalidateUserSessions(UUID userId) {
        redisTemplate.opsForValue().set(
                USER_INVALIDATED_PREFIX + userId,
                "invalidated",
                jwtProperties.getExpiration(), TimeUnit.MILLISECONDS
        );
    }

    public boolean isUserInvalidated(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(USER_INVALIDATED_PREFIX + userId));
    }
}
