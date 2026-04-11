package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.PasswordResetBody;
import com.rubymusic.auth.dto.PasswordResetRequestBody;
import com.rubymusic.auth.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PasswordController implements PasswordApi {

    private final PasswordResetService passwordResetService;

    @Override
    public ResponseEntity<Void> requestPasswordReset(PasswordResetRequestBody body) {
        passwordResetService.requestPasswordReset(body.getEmail());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resetPassword(PasswordResetBody body) {
        passwordResetService.resetPassword(body.getEmail(), body.getCode(), body.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
