package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.model.EmailVerification;
import com.rubymusic.auth.model.RefreshToken;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.AuthProvider;
import com.rubymusic.auth.model.enums.Gender;
import com.rubymusic.auth.model.enums.VerificationType;
import com.rubymusic.auth.repository.EmailVerificationRepository;
import com.rubymusic.auth.repository.RefreshTokenRepository;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.AuthService;
import com.rubymusic.auth.service.TokenPair;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final PrivateKey jwtPrivateKey;

    @Value("${jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-days:30}")
    private long refreshTokenExpirationDays;

    @Value("${spring.mail.username:noreply@rubymusic.com}")
    private String mailFrom;

    // ── Registration ──────────────────────────────────────────────────────────

    @Override
    public User registerWithEmail(String email, String rawPassword, String displayName,
                                  LocalDate birthDate, Gender gender,
                                  boolean acceptedTerms, boolean acceptedPrivacyPolicy) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName(displayName)
                .birthDate(birthDate)
                .gender(gender)
                .authProvider(AuthProvider.EMAIL)
                .acceptedTerms(acceptedTerms)
                .acceptedPrivacyPolicy(acceptedPrivacyPolicy)
                .build();

        user = userRepository.save(user);
        sendOtp(email, VerificationType.REGISTER);
        log.info("User registered: {}", user.getId());
        return user;
    }

    // ── OTP verification ──────────────────────────────────────────────────────

    @Override
    public void verifyEmailOtp(String email, String code, VerificationType type) {
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(email, type)
                .orElseThrow(() -> new IllegalArgumentException("No pending verification found"));

        if (!verification.isValid()) {
            throw new IllegalArgumentException("OTP is expired or already used");
        }
        if (!verification.getCode().equals(code)) {
            throw new IllegalArgumentException("Invalid OTP code");
        }

        verification.setUsedAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);

        if (type == VerificationType.REGISTER) {
            userRepository.findByEmail(email).ifPresent(u -> {
                u.setIsEmailVerified(true);
                userRepository.save(u);
            });
        }
    }

    @Override
    public void resendOtp(String email, VerificationType type) {
        if (type == VerificationType.REGISTER && !userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email not registered");
        }
        sendOtp(email, type);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Override
    public TokenPair loginWithEmail(String email, String rawPassword, String deviceInfo) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!user.getIsEmailVerified()) {
            throw new IllegalStateException("Email not verified");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return issueTokenPair(user, deviceInfo);
    }

    @Override
    public TokenPair loginWithGoogle(String googleIdToken, String deviceInfo) {
        // Requires google-auth-library-java. Stub returns 501 via UnsupportedOperationException.
        throw new UnsupportedOperationException("Google OAuth not yet implemented");
    }

    // ── Token management ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public TokenPair refreshAccessToken(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (!stored.isValid()) {
            throw new IllegalArgumentException("Refresh token is expired or revoked");
        }

        String newAccessToken = generateAccessToken(stored.getUser());
        return new TokenPair(newAccessToken, rawRefreshToken, accessTokenExpirationMs);
    }

    @Override
    public void logout(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    @Override
    public void logoutAll(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    // ── Password reset ────────────────────────────────────────────────────────

    @Override
    public void requestPasswordReset(String email) {
        if (!userRepository.existsByEmail(email)) {
            log.debug("Password reset requested for unknown email (ignored): {}", email);
            return;
        }
        sendOtp(email, VerificationType.PASSWORD_RESET);
    }

    @Override
    public void resetPassword(String email, String code, String newRawPassword) {
        verifyEmailOtp(email, code, VerificationType.PASSWORD_RESET);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(user.getId(), LocalDateTime.now());
        log.info("Password reset completed for user: {}", user.getId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private TokenPair issueTokenPair(User user, String deviceInfo) {
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(rawRefreshToken))
                .deviceInfo(deviceInfo)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(generateAccessToken(user), rawRefreshToken, accessTokenExpirationMs);
    }

    private String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claims(Map.of(
                        "email", user.getEmail(),
                        "displayName", user.getDisplayName(),
                        "profilePhotoUrl", user.getProfilePhotoUrl() != null ? user.getProfilePhotoUrl() : "",
                        "role", user.getRole().name()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(jwtPrivateKey)   // RS256 — jjwt auto-selects algorithm
                .compact();
    }

    private void sendOtp(String email, VerificationType type) {
        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));

        EmailVerification verification = EmailVerification.builder()
                .email(email)
                .code(code)
                .type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        emailVerificationRepository.save(verification);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject(type == VerificationType.REGISTER
                ? "Ruby Music — Verify your email"
                : "Ruby Music — Password reset code");
        message.setText("Your verification code is: " + code + "\nExpires in 10 minutes.");
        mailSender.send(message);
    }

    /** SHA-256 deterministic hash — safe to use as lookup key for refresh tokens */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
