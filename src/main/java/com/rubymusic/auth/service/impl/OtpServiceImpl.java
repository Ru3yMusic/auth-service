package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.exception.InvalidOtpException;
import com.rubymusic.auth.exception.RateLimitExceededException;
import com.rubymusic.auth.model.EmailVerification;
import com.rubymusic.auth.model.enums.VerificationType;
import com.rubymusic.auth.repository.EmailVerificationRepository;
import com.rubymusic.auth.service.OtpService;
import com.rubymusic.auth.service.RateLimitService;
import com.rubymusic.auth.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OtpServiceImpl implements OtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationRepository emailVerificationRepository;
    private final JavaMailSender mailSender;
    private final RateLimitService rateLimitService;
    private final TaskExecutor taskExecutor;

    @Value("${otp.expiration-minutes:10}")
    private int otpExpirationMinutes;

    @Value("${spring.mail.username:noreply@rubymusic.com}")
    private String mailFrom;

    // ── generateAndSend ───────────────────────────────────────────────────────

    @Override
    public void generateAndSend(String email, VerificationType type) {
        // Rate-limit check before any DB work
        rateLimitService.checkRateLimit("otp:" + email);

        String rawCode = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
        String hashedCode = HashUtil.sha256(rawCode);

        EmailVerification verification = EmailVerification.builder()
                .email(email)
                .code(hashedCode)
                .type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                .build();
        emailVerificationRepository.save(verification);

        // Build the mail message now (captures rawCode in closure) but send
        // only AFTER the transaction commits so the OTP row is durable first.
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject(type == VerificationType.REGISTER
                ? "Ruby Music — Verify your email"
                : "Ruby Music — Password reset code");
        message.setText("Your verification code is: " + rawCode
                + "\nExpires in " + otpExpirationMinutes + " minutes.");

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(() -> {
                    mailSender.send(message);
                    log.info("OTP dispatched to {} for type {}", email, type);
                });
            }
        });
    }

    // ── verify ────────────────────────────────────────────────────────────────

    @Override
    public void verify(String email, String code, VerificationType type) {
        String hashedCode = HashUtil.sha256(code);

        EmailVerification ev = emailVerificationRepository
                .findTopByEmailAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(email, type)
                .orElseThrow(() -> new InvalidOtpException("No pending verification found"));

        // Reject immediately if already locked due to prior failures
        if (ev.isLocked()) {
            throw new RateLimitExceededException("OTP is locked due to too many failed attempts");
        }

        if (!ev.isValid()) {
            throw new InvalidOtpException("OTP is expired or already used");
        }

        if (!ev.getCode().equals(hashedCode)) {
            ev.setAttempts(ev.getAttempts() + 1);
            if (ev.getAttempts() >= MAX_ATTEMPTS) {
                ev.setLocked(true);
                emailVerificationRepository.save(ev);
                throw new RateLimitExceededException("Too many failed attempts — OTP locked");
            }
            emailVerificationRepository.save(ev);
            throw new InvalidOtpException("Invalid OTP code");
        }

        // Correct code — mark as consumed
        ev.setUsedAt(LocalDateTime.now());
        emailVerificationRepository.save(ev);
        log.info("OTP verified for {} type {}", email, type);
    }
}
