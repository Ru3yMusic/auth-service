package com.rubymusic.auth.service;

import com.rubymusic.auth.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
        ReflectionTestUtils.setField(rateLimitService, "maxRequests", 5);
        ReflectionTestUtils.setField(rateLimitService, "windowMinutes", 15);
    }

    @Test
    void first5CallsPass() {
        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> rateLimitService.checkRateLimit("otp:test@example.com"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void sixthCallThrowsRateLimitExceeded() {
        for (int i = 0; i < 5; i++) {
            rateLimitService.checkRateLimit("otp:test@example.com");
        }
        assertThatThrownBy(() -> rateLimitService.checkRateLimit("otp:test@example.com"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void differentKeysAreTrackedIndependently() {
        for (int i = 0; i < 5; i++) {
            rateLimitService.checkRateLimit("otp:user1@example.com");
        }
        // Different key must still have full quota
        assertThatCode(() -> rateLimitService.checkRateLimit("otp:user2@example.com"))
                .doesNotThrowAnyException();
    }
}
