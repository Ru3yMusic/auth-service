package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.exception.AccountBlockedException;
import com.rubymusic.auth.exception.EmailNotVerifiedException;
import com.rubymusic.auth.exception.InvalidCredentialsException;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.LoginService;
import com.rubymusic.auth.service.TokenPair;
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
public class LoginServiceImpl implements LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Override
    public TokenPair login(String email, String password, String deviceInfo) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new EmailNotVerifiedException("Email address has not been verified");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        // Check AFTER credentials: this avoids leaking whether a given email
        // corresponds to a blocked account to unauthenticated callers.
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AccountBlockedException("Account is blocked", user.getBlockReason());
        }

        TokenPair pair = tokenService.issueTokenPair(user, deviceInfo);
        log.info("User {} logged in successfully", user.getId());
        return pair;
    }
}
