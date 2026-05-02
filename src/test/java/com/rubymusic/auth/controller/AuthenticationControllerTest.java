package com.rubymusic.auth.controller;

import com.rubymusic.auth.exception.GlobalExceptionHandler;
import com.rubymusic.auth.exception.InvalidCredentialsException;
import com.rubymusic.auth.exception.UnauthorizedException;
import com.rubymusic.auth.service.LoginService;
import com.rubymusic.auth.service.TokenPair;
import com.rubymusic.auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    @Mock
    private LoginService loginService;

    @Mock
    private TokenService tokenService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthenticationController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200_withTokenPair() throws Exception {
        TokenPair pair = new TokenPair("access-jwt", "refresh-jwt", 3_600_000L);
        when(loginService.login(eq("alice@example.com"), eq("Password1!"), any()))
                .thenReturn(pair);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"Password1!\",\"deviceInfo\":\"web\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        verify(loginService).login("alice@example.com", "Password1!", "web");
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(loginService.login(any(), any(), any()))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"wrong\",\"deviceInfo\":\"web\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── loginWithGoogle ───────────────────────────────────────────────────────

    @Test
    void loginWithGoogle_notImplemented_returns501() throws Exception {
        // Controller throws UnsupportedOperationException → handler maps it to 501
        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"googleIdToken\":\"google-id-token\"}"))
                .andExpect(status().isNotImplemented());
    }

    // ── refreshToken ──────────────────────────────────────────────────────────

    @Test
    void refreshToken_validToken_returns200() throws Exception {
        TokenPair pair = new TokenPair("new-access", "new-refresh", 3_600_000L);
        when(tokenService.refreshAccessToken("old-refresh-token")).thenReturn(pair);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));

        verify(tokenService).refreshAccessToken("old-refresh-token");
    }

    @Test
    void refreshToken_revokedToken_returns401() throws Exception {
        when(tokenService.refreshAccessToken(any()))
                .thenThrow(new UnauthorizedException("Refresh token already used"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"stolen-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_validToken_returns204() throws Exception {
        doNothing().when(tokenService).logout("a-refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"a-refresh-token\"}"))
                .andExpect(status().isNoContent());

        verify(tokenService).logout("a-refresh-token");
    }

    // ── logoutAll ─────────────────────────────────────────────────────────────

    @Test
    void logoutAll_validHeader_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        doNothing().when(tokenService).logoutAll(userId);

        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isNoContent());

        verify(tokenService).logoutAll(userId);
    }

    @Test
    void logoutAll_missingHeader_returns401() throws Exception {
        when(httpRequest.getHeader("X-User-Id")).thenReturn(null);

        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAll_serviceFails_propagatesError() throws Exception {
        UUID userId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        doThrow(new IllegalStateException("DB unreachable"))
                .when(tokenService).logoutAll(userId);

        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isBadRequest());
    }
}
