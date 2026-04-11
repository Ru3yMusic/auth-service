package com.rubymusic.auth.config;

import com.rubymusic.auth.repository.EmailVerificationRepository;
import com.rubymusic.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class AuthSchedulingConfig {

    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;

    /**
     * Runs every hour at :00. Removes expired and revoked refresh tokens.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Scheduled: cleaning up expired/revoked refresh tokens");
        refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now());
        log.info("Scheduled: refresh token cleanup complete");
    }

    /**
     * Runs every hour at :30. Removes expired email verification records.
     */
    @Scheduled(cron = "0 30 * * * *")
    @Transactional
    public void cleanupExpiredVerifications() {
        log.info("Scheduled: cleaning up expired email verifications");
        emailVerificationRepository.deleteExpiredVerifications(LocalDateTime.now());
        log.info("Scheduled: email verification cleanup complete");
    }
}
