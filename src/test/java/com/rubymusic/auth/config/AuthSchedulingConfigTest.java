package com.rubymusic.auth.config;

import com.rubymusic.auth.repository.EmailVerificationRepository;
import com.rubymusic.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthSchedulingConfigTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @InjectMocks
    private AuthSchedulingConfig authSchedulingConfig;

    @Test
    void cleanupExpiredTokens_invokesDeleteExpiredAndRevoked() {
        authSchedulingConfig.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredAndRevoked(any(LocalDateTime.class));
    }

    @Test
    void cleanupExpiredVerifications_invokesDeleteExpiredVerifications() {
        authSchedulingConfig.cleanupExpiredVerifications();

        verify(emailVerificationRepository).deleteExpiredVerifications(any(LocalDateTime.class));
    }
}
