package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.client.PlaylistServiceClient;
import com.rubymusic.auth.exception.UserNotFoundException;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.Gender;
import com.rubymusic.auth.model.enums.VerificationType;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.OtpService;
import com.rubymusic.auth.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import org.mockito.InOrder;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpService otpService;
    @Mock private TokenService tokenService;   // injected per task spec; not used in these tests
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PlaylistServiceClient playlistServiceClient;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    // ── registerWithEmail ─────────────────────────────────────────────────────

    @Test
    void registerWithEmail_savesUserWithAcceptsMarketing_true_BUG06() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        User saved = User.builder()
                .id(UUID.randomUUID())
                .email("a@b.com")
                .displayName("Alice")
                .acceptsMarketing(true)
                .build();
        when(userRepository.save(any())).thenReturn(saved);

        registrationService.registerWithEmail(
                "a@b.com", "Password1!", "Alice",
                LocalDate.of(2000, 1, 1), Gender.FEMENINO,
                true, true, true);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getAcceptsMarketing()).isTrue();
    }

    @Test
    void registerWithEmail_savesUserWithAcceptsMarketing_false_BUG06() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        User saved = User.builder()
                .id(UUID.randomUUID())
                .email("a@b.com")
                .displayName("Alice")
                .acceptsMarketing(false)
                .build();
        when(userRepository.save(any())).thenReturn(saved);

        registrationService.registerWithEmail(
                "a@b.com", "Password1!", "Alice",
                LocalDate.of(2000, 1, 1), Gender.FEMENINO,
                true, true, false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getAcceptsMarketing()).isFalse();
    }

    @Test
    void registerWithEmail_sendsOtp_afterSave() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(
                User.builder().id(UUID.randomUUID()).email("a@b.com").displayName("Alice").build());

        registrationService.registerWithEmail(
                "a@b.com", "pass", "Alice",
                LocalDate.of(1999, 5, 20), Gender.MASCULINO,
                true, true, false);

        // OTP must be dispatched AFTER the user is persisted
        InOrder order = inOrder(userRepository, otpService);
        order.verify(userRepository).save(any());
        order.verify(otpService).generateAndSend("a@b.com", VerificationType.REGISTER);
    }

    @Test
    void registerWithEmail_throwsIllegalArgument_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail("dup@b.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.registerWithEmail(
                "dup@b.com", "pass", "Bob",
                LocalDate.of(1990, 1, 1), Gender.MASCULINO,
                true, true, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── verifyEmailOtp (BUG-01) ───────────────────────────────────────────────

    @Test
    void verifyEmailOtp_checkUserExistsBEFOREConsumingOtp_BUG01() {
        // BUG-01: user existence must be verified BEFORE the OTP is consumed
        when(userRepository.findByEmail("ghost@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.verifyEmailOtp("ghost@b.com", "123456"))
                .isInstanceOf(UserNotFoundException.class);

        // OTP must NOT be touched for a non-existent user
        verify(otpService, never()).verify(any(), any(), any());
    }

    @Test
    void verifyEmailOtp_setsEmailVerified_andCreatesSystemPlaylist() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("a@b.com")
                .isEmailVerified(false)
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        registrationService.verifyEmailOtp("a@b.com", "654321");

        verify(otpService).verify("a@b.com", "654321", VerificationType.REGISTER);
        assertThat(user.getIsEmailVerified()).isTrue();
        verify(userRepository).save(user);
        verify(playlistServiceClient).createSystemPlaylist(user.getId());
    }

    // ── resendOtp ─────────────────────────────────────────────────────────────

    @Test
    void resendOtp_PASSWORD_RESET_silentlyReturnsForUnknownEmail_BUG04() {
        when(userRepository.existsByEmail("nobody@b.com")).thenReturn(false);

        assertThatNoException().isThrownBy(() ->
                registrationService.resendOtp("nobody@b.com", VerificationType.PASSWORD_RESET));

        verify(otpService, never()).generateAndSend(any(), any());
    }

    @Test
    void resendOtp_REGISTER_throwsUserNotFound_forUnknownEmail() {
        when(userRepository.existsByEmail("nobody@b.com")).thenReturn(false);

        assertThatThrownBy(() ->
                registrationService.resendOtp("nobody@b.com", VerificationType.REGISTER))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void resendOtp_sendsOtp_forKnownEmail() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        registrationService.resendOtp("a@b.com", VerificationType.REGISTER);

        verify(otpService).generateAndSend("a@b.com", VerificationType.REGISTER);
    }
}
