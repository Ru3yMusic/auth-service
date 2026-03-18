package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.PasswordResetBody;
import com.rubymusic.auth.dto.PasswordResetRequestBody;
import com.rubymusic.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PasswordController implements PasswordApi {

    private final AuthService authService;

    @Override
    public ResponseEntity<Void> requestPasswordReset(PasswordResetRequestBody body) {
        authService.requestPasswordReset(body.getEmail());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resetPassword(PasswordResetBody body) {
        authService.resetPassword(body.getEmail(), body.getCode(), body.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
