package com.rubymusic.auth.service;

import com.rubymusic.auth.model.enums.VerificationType;

/**
 * Handles OTP generation (with SHA-256 hashing), delivery via email,
 * and secure verification with attempt tracking and lockout.
 */
public interface OtpService {

    /**
     * Generates a 6-digit OTP, stores it hashed (SHA-256), and sends
     * the plaintext code to the given email address.
     */
    void generateAndSend(String email, VerificationType type);

    /**
     * Verifies a submitted OTP code against the stored hash.
     * Increments attempt counter on failure; locks after 5 wrong attempts.
     *
     * @throws com.rubymusic.auth.exception.RateLimitExceededException if locked or 5th wrong attempt
     * @throws com.rubymusic.auth.exception.InvalidOtpException        if code is wrong (< 5 attempts)
     */
    void verify(String email, String code, VerificationType type);
}
