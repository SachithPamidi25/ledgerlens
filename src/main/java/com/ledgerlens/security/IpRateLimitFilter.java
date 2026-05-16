package com.ledgerlens.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limit.ip.max-requests-per-minute:200}")
    private int maxRequestsPerMinute;

    @Value("${rate-limit.ip.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    private static final long WINDOW_SECONDS = 60;

    // Same atomic Lua pattern as RateLimitService — INCR + conditional EXPIRE in one round-trip
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
            "local count = redis.call('INCR', KEYS[1])\n" +
            "if count == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return count",
            Long.class
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String ip = extractClientIp(request);
        String key = "ip_ratelimit:" + ip;

        Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(WINDOW_SECONDS)
        );

        if (count != null && count > maxRequestsPerMinute) {
            log.warn("IP rate limit exceeded: ip={} count={}", ip, count);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Too many requests. Limit: " + maxRequestsPerMinute + " req/min.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Extracts the real client IP, respecting proxy/load balancer headers.
     * X-Forwarded-For contains a comma-separated list — the leftmost is the original client.
     */
    private String extractClientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
