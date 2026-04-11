package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.exception.UnauthorizedException;
import com.rubymusic.auth.model.RefreshToken;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.repository.RefreshTokenRepository;
import com.rubymusic.auth.service.TokenPair;
import com.rubymusic.auth.service.TokenService;
import com.rubymusic.auth.util.HashUtil;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TokenServiceImpl implements TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PrivateKey jwtPrivateKey;

    @Value("${jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-days:30}")
    private long refreshTokenExpirationDays;

    // ── generateAccessToken ───────────────────────────────────────────────────

    @Override
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claims(Map.of(
                        "email", user.getEmail(),
                        "displayName", user.getDisplayName(),
                        "profilePhotoUrl", user.getProfilePhotoUrl() != null ? user.getProfilePhotoUrl() : "",
                        "role", user.getRole().name()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(jwtPrivateKey)
                .compact();
    }

    // ── issueTokenPair ────────────────────────────────────────────────────────

    @Override
    public TokenPair issueTokenPair(User user, String deviceInfo) {
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(HashUtil.sha256(rawRefreshToken))
                .deviceInfo(deviceInfo)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(generateAccessToken(user), rawRefreshToken, accessTokenExpirationMs);
    }

    // ── refreshAccessToken (with rotation + theft detection) ─────────────────

    @Override
    public TokenPair refreshAccessToken(String rawRefreshToken) {
        String hash = HashUtil.sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));

        // Token reuse on an already-revoked token → theft detected
        if (stored.isRevoked()) {
            log.warn("Revoked refresh token reuse detected for user {} — revoking all sessions",
                    stored.getUser().getId());
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId(), LocalDateTime.now());
            throw new UnauthorizedException("Token reuse detected — all sessions have been revoked");
        }

        if (stored.isExpired()) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        // Revoke the consumed token
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        // Issue a fresh pair (saves the new refresh token internally)
        return issueTokenPair(stored.getUser(), stored.getDeviceInfo());
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Override
    public void logout(String rawRefreshToken) {
        String hash = HashUtil.sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    // ── logoutAll ─────────────────────────────────────────────────────────────

    @Override
    public void logoutAll(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }
}
