package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.client.PlaylistServiceClient;
import com.rubymusic.auth.exception.UserNotFoundException;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.AuthProvider;
import com.rubymusic.auth.model.enums.Gender;
import com.rubymusic.auth.model.enums.VerificationType;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.OtpService;
import com.rubymusic.auth.service.RegistrationService;
import com.rubymusic.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final TokenService tokenService;  // injected per task spec
    private final PasswordEncoder passwordEncoder;
    private final PlaylistServiceClient playlistServiceClient;

    // ── registerWithEmail ─────────────────────────────────────────────────────

    @Override
    public User registerWithEmail(String email, String password, String displayName,
                                  LocalDate birthDate, Gender gender,
                                  boolean acceptedTerms, boolean acceptedPrivacyPolicy,
                                  boolean acceptsMarketing) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(displayName)
                .birthDate(birthDate)
                .gender(gender)
                .authProvider(AuthProvider.EMAIL)
                .acceptedTerms(acceptedTerms)
                .acceptedPrivacyPolicy(acceptedPrivacyPolicy)
                .acceptsMarketing(acceptsMarketing)   // BUG-06 fix: was silently ignored before
                .build();

        user = userRepository.save(user);
        otpService.generateAndSend(email, VerificationType.REGISTER);
        log.info("User registered: {}", user.getId());
        return user;
    }

    // ── verifyEmailOtp ────────────────────────────────────────────────────────

    @Override
    public void verifyEmailOtp(String email, String code) {
        // BUG-01 fix: check user exists FIRST — before consuming the OTP.
        // The original AuthServiceImpl called otpService.verify before this check,
        // which could consume the OTP for a non-existent account.
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));

        otpService.verify(email, code, VerificationType.REGISTER);

        user.setIsEmailVerified(true);
        userRepository.save(user);

        try {
            playlistServiceClient.createSystemPlaylist(user.getId());
        } catch (Exception ex) {
            log.warn("Could not create system playlist for user {}: {}", user.getId(), ex.getMessage());
        }

        log.info("Email verified for user: {}", user.getId());
    }

    // ── resendOtp ─────────────────────────────────────────────────────────────

    @Override
    public void resendOtp(String email, VerificationType type) {
        if (!userRepository.existsByEmail(email)) {
            if (type == VerificationType.PASSWORD_RESET) {
                // BUG-04 fix: anti-enumeration — silently return for unknown emails
                log.debug("resendOtp PASSWORD_RESET: email not found, silently ignoring: {}", email);
                return;
            }
            throw new UserNotFoundException("Email not registered: " + email);
        }
        otpService.generateAndSend(email, type);
    }
}
