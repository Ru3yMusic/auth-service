package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.exception.EmailNotVerifiedException;
import com.rubymusic.auth.exception.InvalidCredentialsException;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.TokenPair;
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
class LoginServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;

    @InjectMocks
    private LoginServiceImpl loginService;

    @Test
    void login_throwsInvalidCredentials_forUnknownEmail() {
        when(userRepository.findByEmail("nobody@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login("nobody@b.com", "pass", null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_throwsEmailNotVerified_whenEmailNotVerified() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("a@b.com")
                .isEmailVerified(false)
                .passwordHash("hashed")
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login("a@b.com", "pass", null))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void login_throwsInvalidCredentials_forWrongPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("a@b.com")
                .isEmailVerified(true)
                .passwordHash("hashed")
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> loginService.login("a@b.com", "wrong", null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_returnsTokenPair_forValidCredentials() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("a@b.com")
                .isEmailVerified(true)
                .passwordHash("hashed")
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        TokenPair expected = new TokenPair("access-jwt", "refresh-token", 900_000L);
        when(tokenService.issueTokenPair(user, "android-device")).thenReturn(expected);

        TokenPair result = loginService.login("a@b.com", "correct", "android-device");

        assertThat(result).isEqualTo(expected);
        verify(tokenService).issueTokenPair(user, "android-device");
    }
}
