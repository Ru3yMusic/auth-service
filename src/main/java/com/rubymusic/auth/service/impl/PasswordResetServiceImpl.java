package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.exception.UserNotFoundException;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.VerificationType;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.OtpService;
import com.rubymusic.auth.service.PasswordResetService;
import com.rubymusic.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    // ── requestPasswordReset ──────────────────────────────────────────────────

    @Override
    public void requestPasswordReset(String email) {
        if (!userRepository.existsByEmail(email)) {
            log.debug("Password reset requested for unknown email (ignored): {}", email);
            return;  // anti-enumeration: same response for known and unknown emails
        }
        otpService.generateAndSend(email, VerificationType.PASSWORD_RESET);
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Override
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));

        otpService.verify(email, code, VerificationType.PASSWORD_RESET);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke all active sessions after password change
        tokenService.logoutAll(user.getId());

        log.info("Password reset completed for user: {}", user.getId());
    }
}
