package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.exception.UnauthorizedException;
import com.rubymusic.auth.model.RefreshToken;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.AuthProvider;
import com.rubymusic.auth.model.enums.Gender;
import com.rubymusic.auth.model.enums.UserRole;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.repository.RefreshTokenRepository;
import com.rubymusic.auth.service.TokenPair;
import com.rubymusic.auth.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenServiceTest {

    private RefreshTokenRepository refreshTokenRepository;
    private TokenServiceImpl tokenService;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository = mock(RefreshTokenRepository.class);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        tokenService = new TokenServiceImpl(refreshTokenRepository, kp.getPrivate());
        ReflectionTestUtils.setField(tokenService, "accessTokenExpirationMs", 900_000L);
        ReflectionTestUtils.setField(tokenService, "refreshTokenExpirationDays", 30L);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .displayName("Test User")
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.EMAIL)
                .isEmailVerified(true)
                .build();
    }

    // ── generateAccessToken ───────────────────────────────────────────────────

    @Test
    void generateAccessToken_returnsValidJwtWithThreeParts() {
        String token = tokenService.generateAccessToken(testUser);

        assertThat(token).isNotBlank();
        // A compact JWS always has exactly 3 dot-separated parts
        assertThat(token.split("\\.")).hasSize(3);
    }

    // ── issueTokenPair ────────────────────────────────────────────────────────

    @Test
    void issueTokenPair_savesHashedRefreshToken_returnsBothTokens() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenPair pair = tokenService.issueTokenPair(testUser, "Mozilla/5.0");

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        // Stored hash must match the SHA-256 of the raw token returned to the caller
        assertThat(saved.getTokenHash()).isEqualTo(HashUtil.sha256(pair.refreshToken()));
        assertThat(saved.getDeviceInfo()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getUser()).isEqualTo(testUser);
    }

    // ── refreshAccessToken: token rotation ───────────────────────────────────

    @Test
    void refreshAccessToken_rotatesToken_revokesOldAndReturnsNew() {
        String rawToken = UUID.randomUUID().toString();
        RefreshToken stored = activeToken(rawToken);

        when(refreshTokenRepository.findByTokenHash(HashUtil.sha256(rawToken)))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenPair pair = tokenService.refreshAccessToken(rawToken);

        // Old token must be revoked
        assertThat(stored.getRevokedAt()).isNotNull();
        // New refresh token must differ from the old one
        assertThat(pair.refreshToken()).isNotEqualTo(rawToken);
        assertThat(pair.accessToken()).isNotBlank();
        // 2 saves: one for revocation, one for the new refresh token
        verify(refreshTokenRepository, times(2)).save(any());
    }

    // ── refreshAccessToken: theft detection ───────────────────────────────────

    @Test
    void refreshAccessToken_withRevokedToken_revokesAllAndThrowsUnauthorized() {
        String rawToken = UUID.randomUUID().toString();
        RefreshToken revoked = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(HashUtil.sha256(rawToken))
                .deviceInfo("device")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .revokedAt(LocalDateTime.now().minusHours(1))   // already revoked
                .build();

        when(refreshTokenRepository.findByTokenHash(HashUtil.sha256(rawToken)))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> tokenService.refreshAccessToken(rawToken))
                .isInstanceOf(UnauthorizedException.class);

        // Must revoke ALL sessions for the user (theft detection)
        verify(refreshTokenRepository).revokeAllByUserId(eq(testUser.getId()), any(LocalDateTime.class));
        // Must NOT save a new token
        verify(refreshTokenRepository, never()).save(any());
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_setsRevokedAtOnMatchingToken() {
        String rawToken = UUID.randomUUID().toString();
        RefreshToken stored = activeToken(rawToken);

        when(refreshTokenRepository.findByTokenHash(HashUtil.sha256(rawToken)))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tokenService.logout(rawToken);

        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void logout_unknownToken_doesNothing() {
        String rawToken = UUID.randomUUID().toString();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        // Must not throw
        assertThatCode(() -> tokenService.logout(rawToken)).doesNotThrowAnyException();
        verify(refreshTokenRepository, never()).save(any());
    }

    // ── logoutAll ─────────────────────────────────────────────────────────────

    @Test
    void logoutAll_delegatesToBulkRevokeQuery() {
        UUID userId = testUser.getId();

        tokenService.logoutAll(userId);

        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any(LocalDateTime.class));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RefreshToken activeToken(String rawToken) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(HashUtil.sha256(rawToken))
                .deviceInfo("device")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }
}
