package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.exception.UserNotFoundException;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.VerificationType;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.OtpService;
import com.rubymusic.auth.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpService otpService;
    @Mock private TokenService tokenService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    // ── requestPasswordReset ───────────────────────────────────────────────────

    @Test
    void requestPasswordReset_silentlyReturns_forUnknownEmail() {
        when(userRepository.existsByEmail("nobody@b.com")).thenReturn(false);

        assertThatNoException().isThrownBy(() ->
                passwordResetService.requestPasswordReset("nobody@b.com"));

        verify(otpService, never()).generateAndSend(any(), any());
    }

    @Test
    void requestPasswordReset_sendsPasswordResetOtp_forKnownEmail() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        passwordResetService.requestPasswordReset("a@b.com");

        verify(otpService).generateAndSend("a@b.com", VerificationType.PASSWORD_RESET);
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_verifiesOtp_updatesHash_andRevokesAllSessions() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("a@b.com")
                .passwordHash("old-hash")
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("new-hash");

        passwordResetService.resetPassword("a@b.com", "123456", "NewPass1!");

        verify(otpService).verify("a@b.com", "123456", VerificationType.PASSWORD_RESET);
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
        verify(tokenService).logoutAll(user.getId());
    }

    @Test
    void resetPassword_throwsUserNotFoundException_forUnknownEmail() {
        when(userRepository.findByEmail("nobody@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                passwordResetService.resetPassword("nobody@b.com", "123456", "Pass1!"))
                .isInstanceOf(UserNotFoundException.class);

        verify(otpService, never()).verify(any(), any(), any());
    }
}
