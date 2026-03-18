package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.RegisterRequest;
import com.rubymusic.auth.dto.ResendOtpRequest;
import com.rubymusic.auth.dto.UserResponse;
import com.rubymusic.auth.dto.VerifyOtpRequest;
import com.rubymusic.auth.mapper.UserMapper;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.Gender;
import com.rubymusic.auth.model.enums.VerificationType;
import com.rubymusic.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegistrationController implements RegistrationApi {

    private final AuthService authService;
    private final UserMapper userMapper;

    @Override
    public ResponseEntity<UserResponse> register(RegisterRequest body) {
        User user = authService.registerWithEmail(
                body.getEmail(),
                body.getPassword(),
                body.getDisplayName(),
                body.getBirthDate(),
                Gender.valueOf(body.getGender().name()),
                body.getAcceptedTerms(),
                body.getAcceptedPrivacyPolicy()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toDto(user));
    }

    @Override
    public ResponseEntity<Void> verifyEmail(VerifyOtpRequest body) {
        authService.verifyEmailOtp(
                body.getEmail(),
                body.getCode(),
                VerificationType.valueOf(body.getType().name())
        );
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resendOtp(ResendOtpRequest body) {
        authService.resendOtp(
                body.getEmail(),
                VerificationType.valueOf(body.getType().name())
        );
        return ResponseEntity.noContent().build();
    }
}
