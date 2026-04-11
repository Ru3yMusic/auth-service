package com.rubymusic.auth.service;

import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.Gender;
import com.rubymusic.auth.model.enums.VerificationType;

import java.time.LocalDate;

/**
 * Handles the full email-based registration flow:
 * sign-up, OTP verification, and OTP resend.
 */
public interface RegistrationService {

    /**
     * Creates a new user, encodes the password, persists acceptsMarketing (BUG-06 fix),
     * and dispatches a REGISTER OTP. Returns the saved {@link User} entity.
     *
     * @throws IllegalArgumentException if the email is already registered
     */
    User registerWithEmail(String email, String password, String displayName,
                           LocalDate birthDate, Gender gender,
                           boolean acceptedTerms, boolean acceptedPrivacyPolicy,
                           boolean acceptsMarketing);

    /**
     * Verifies the REGISTER OTP for the given email.
     * <p>
     * BUG-01 fix: checks that the user exists BEFORE consuming the OTP, so a
     * phantom verification cannot mark a non-existent account as verified.
     * On success, sets {@code isEmailVerified = true} and triggers system playlist creation.
     *
     * @throws com.rubymusic.auth.exception.UserNotFoundException       if the email is not registered
     * @throws com.rubymusic.auth.exception.InvalidOtpException        if the OTP is wrong
     * @throws com.rubymusic.auth.exception.RateLimitExceededException if the OTP is locked
     */
    void verifyEmailOtp(String email, String code);

    /**
     * Resends the OTP for the given email and verification type.
     * <p>
     * BUG-04 fix: for {@code PASSWORD_RESET} type, silently returns when the email is
     * unknown (anti-enumeration). For {@code REGISTER} type, throws
     * {@link com.rubymusic.auth.exception.UserNotFoundException} if the email is not found.
     */
    void resendOtp(String email, VerificationType type);
}
