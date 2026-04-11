package com.rubymusic.auth.service;

/**
 * Handles the password recovery flow:
 * OTP request and password reset with session revocation.
 */
public interface PasswordResetService {

    /**
     * Sends a PASSWORD_RESET OTP to the given email.
     * Silently ignores unknown emails to prevent user enumeration.
     */
    void requestPasswordReset(String email);

    /**
     * Verifies the PASSWORD_RESET OTP, updates the password hash,
     * and revokes all active sessions for the user.
     *
     * @throws com.rubymusic.auth.exception.UserNotFoundException       if the email is not registered
     * @throws com.rubymusic.auth.exception.InvalidOtpException        if the OTP is wrong
     * @throws com.rubymusic.auth.exception.RateLimitExceededException if the OTP is locked
     */
    void resetPassword(String email, String code, String newPassword);
}
