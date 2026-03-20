package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.GoogleLoginRequest;
import com.rubymusic.auth.dto.LoginRequest;
import com.rubymusic.auth.dto.RefreshTokenRequest;
import com.rubymusic.auth.dto.TokenResponse;
import com.rubymusic.auth.service.AuthService;
import com.rubymusic.auth.service.TokenPair;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AuthenticationController implements AuthenticationApi {

    private final AuthService authService;
    private final HttpServletRequest httpRequest;

    @Override
    public ResponseEntity<TokenResponse> login(LoginRequest body) {
        TokenPair pair = authService.loginWithEmail(
                body.getEmail(),
                body.getPassword(),
                body.getDeviceInfo()
        );
        return ResponseEntity.ok(toResponse(pair));
    }

    @Override
    public ResponseEntity<TokenResponse> loginWithGoogle(GoogleLoginRequest body) {
        TokenPair pair = authService.loginWithGoogle(
                body.getGoogleIdToken(),
                body.getDeviceInfo()
        );
        return ResponseEntity.ok(toResponse(pair));
    }

    @Override
    public ResponseEntity<TokenResponse> refreshToken(RefreshTokenRequest body) {
        TokenPair pair = authService.refreshAccessToken(body.getRefreshToken());
        return ResponseEntity.ok(toResponse(pair));
    }

    @Override
    public ResponseEntity<Void> logout(RefreshTokenRequest body) {
        authService.logout(body.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> logoutAll() {
        String userId = httpRequest.getHeader("X-User-Id");
        authService.logoutAll(UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    private TokenResponse toResponse(TokenPair pair) {
        return new TokenResponse()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .tokenType("Bearer")
                .expiresIn((int) (pair.accessTokenExpiresInMs() / 1000));
    }
}
