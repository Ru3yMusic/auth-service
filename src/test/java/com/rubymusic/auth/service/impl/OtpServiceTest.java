package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.exception.InvalidOtpException;
import com.rubymusic.auth.exception.RateLimitExceededException;
import com.rubymusic.auth.model.EmailVerification;
import com.rubymusic.auth.model.enums.VerificationType;
import com.rubymusic.auth.repository.EmailVerificationRepository;
import com.rubymusic.auth.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "otpExpirationMinutes", 10);
        ReflectionTestUtils.setField(otpService, "mailFrom", "noreply@rubymusic.com");
    }

    // ── generateAndSend ───────────────────────────────────────────────────────

    @Test
    void generateAndSend_storesHashedCode_notPlaintext() {
        // GIVEN
        when(emailVerificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        otpService.generateAndSend("user@example.com", VerificationType.REGISTER);

        // THEN: stored code must be 64-char SHA-256 hex, not a 6-digit plaintext OTP
        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(captor.capture());

        String storedCode = captor.getValue().getCode();
        assertThat(storedCode).hasSize(64);
        assertThat(storedCode).matches("[a-f0-9]{64}");
    }

    @Test
    void generateAndSend_sendsEmailWithPlaintextCode() {
        // GIVEN
        when(emailVerificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        otpService.generateAndSend("user@example.com", VerificationType.REGISTER);

        // THEN: exactly one email dispatched
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── verify: happy path ────────────────────────────────────────────────────

    @Test
    void verify_correctCode_marksRecordAsUsed() {
        // GIVEN
        String rawCode = "482931";
        EmailVerification ev = buildVerification(HashUtil.sha256(rawCode), 0, false);
        when(emailVerificationRepository
                .findTopByEmailAndTypeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com", VerificationType.REGISTER))
                .thenReturn(Optional.of(ev));
        when(emailVerificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        otpService.verify("user@example.com", rawCode, VerificationType.REGISTER);

        // THEN
        assertThat(ev.getUsedAt()).isNotNull();
        verify(emailVerificationRepository).save(ev);
    }

    // ── verify: wrong code path ───────────────────────────────────────────────

    @Test
    void verify_wrongCode_incrementsAttemptsAndThrowsInvalidOtp() {
        // GIVEN: first wrong attempt (attempts = 0 → becomes 1)
        EmailVerification ev = buildVerification(HashUtil.sha256("123456"), 0, false);
        when(emailVerificationRepository
                .findTopByEmailAndTypeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com", VerificationType.REGISTER))
                .thenReturn(Optional.of(ev));
        when(emailVerificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // WHEN / THEN
        assertThatThrownBy(() -> otpService.verify("user@example.com", "999999", VerificationType.REGISTER))
                .isInstanceOf(InvalidOtpException.class);

        assertThat(ev.getAttempts()).isEqualTo(1);
        assertThat(ev.isLocked()).isFalse();
    }

    @Test
    void verify_fifthWrongAttempt_locksOtpAndThrowsRateLimit() {
        // GIVEN: already at 4 attempts — one more wrong attempt should lock
        EmailVerification ev = buildVerification(HashUtil.sha256("123456"), 4, false);
        when(emailVerificationRepository
                .findTopByEmailAndTypeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com", VerificationType.REGISTER))
                .thenReturn(Optional.of(ev));
        when(emailVerificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // WHEN / THEN
        assertThatThrownBy(() -> otpService.verify("user@example.com", "999999", VerificationType.REGISTER))
                .isInstanceOf(RateLimitExceededException.class);

        assertThat(ev.getAttempts()).isEqualTo(5);
        assertThat(ev.isLocked()).isTrue();
    }

    // ── verify: pre-locked OTP ────────────────────────────────────────────────

    @Test
    void verify_alreadyLockedOtp_throwsRateLimitImmediately() {
        // GIVEN: OTP was previously locked (even with correct code submitted)
        EmailVerification ev = buildVerification(HashUtil.sha256("123456"), 5, true);
        when(emailVerificationRepository
                .findTopByEmailAndTypeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com", VerificationType.REGISTER))
                .thenReturn(Optional.of(ev));

        // WHEN / THEN: must reject immediately without touching attempts
        assertThatThrownBy(() -> otpService.verify("user@example.com", "123456", VerificationType.REGISTER))
                .isInstanceOf(RateLimitExceededException.class);

        verify(emailVerificationRepository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private EmailVerification buildVerification(String hashedCode, int attempts, boolean locked) {
        return EmailVerification.builder()
                .email("user@example.com")
                .code(hashedCode)
                .type(VerificationType.REGISTER)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .attempts(attempts)
                .locked(locked)
                .build();
    }
}
