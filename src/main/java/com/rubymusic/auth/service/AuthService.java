package com.rubymusic.auth.service;

import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.Gender;
import com.rubymusic.auth.model.enums.VerificationType;

import java.time.LocalDate;
import java.util.UUID;

public interface AuthService {

    /**
     * Persists a new user with unverified email and sends a 5-digit OTP.
     * Throws IllegalArgumentException if the email is already registered.
     */
    User registerWithEmail(String email, String rawPassword, String displayName,
                           LocalDate birthDate, Gender gender,
                           boolean acceptedTerms, boolean acceptedPrivacyPolicy);

    /**
     * Validates the OTP for REGISTER or PASSWORD_RESET flows.
     * Marks the user's email as verified on REGISTER.
     */
    void verifyEmailOtp(String email, String code, VerificationType type);

    /**
     * Generates and sends a new OTP, invalidating the previous one implicitly
     * (only the latest unused OTP is checked at validation time).
     */
    void resendOtp(String email, VerificationType type);

    /**
     * Authenticates email + password. Returns a signed JWT access token paired
     * with a raw refresh token.
     */
    TokenPair loginWithEmail(String email, String rawPassword, String deviceInfo);

    /**
     * Validates a Google ID token, upserts the user, and returns both tokens.
     */
    TokenPair loginWithGoogle(String googleIdToken, String deviceInfo);

    /**
     * Issues a new JWT access token from a valid (non-expired, non-revoked) refresh token.
     * Returns the same raw refresh token so the client can re-use it.
     */
    TokenPair refreshAccessToken(String rawRefreshToken);

    /**
     * Revokes one refresh token — logout from the current device.
     */
    void logout(String rawRefreshToken);

    /**
     * Revokes all refresh tokens for the user — logout from every device.
     */
    void logoutAll(UUID userId);

    /**
     * Sends a PASSWORD_RESET OTP. Silently ignores unknown emails to prevent enumeration.
     */
    void requestPasswordReset(String email);

    /**
     * Validates the OTP and sets the new password. Revokes all sessions.
     */
    void resetPassword(String email, String code, String newRawPassword);
}
