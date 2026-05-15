package com.ledgerlens.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    void underLimit_returnsTrue() {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString()))
                .thenReturn(1L);

        assertThat(rateLimitService.tryConsume("user@example.com")).isTrue();
    }

    @Test
    void atLimit_returnsTrue() {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString()))
                .thenReturn(5L);

        assertThat(rateLimitService.tryConsume("user@example.com")).isTrue();
    }

    @Test
    void overLimit_returnsFalse() {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString()))
                .thenReturn(6L);

        assertThat(rateLimitService.tryConsume("user@example.com")).isFalse();
    }

    @Test
    void nullRedisResponse_returnsFalse() {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString()))
                .thenReturn(null);

        assertThat(rateLimitService.tryConsume("user@example.com")).isFalse();
    }
}
