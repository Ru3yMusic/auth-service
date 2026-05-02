package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.UserResponse;
import com.rubymusic.auth.exception.GlobalExceptionHandler;
import com.rubymusic.auth.exception.InvalidOtpException;
import com.rubymusic.auth.exception.UserNotFoundException;
import com.rubymusic.auth.mapper.UserMapper;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.VerificationType;
import com.rubymusic.auth.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RegistrationControllerTest {

    @Mock
    private RegistrationService registrationService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private RegistrationController controller;

    private MockMvc mockMvc;

    private static final String VALID_REGISTER_BODY = """
            {
              "email": "alice@example.com",
              "password": "Password1!",
              "displayName": "Alice",
              "birthDate": "2000-01-15",
              "gender": "FEMENINO",
              "acceptedTerms": true,
              "acceptedPrivacyPolicy": true,
              "acceptsMarketing": false
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_validBody_returns201() throws Exception {
        User savedUser = mock(User.class);
        when(registrationService.registerWithEmail(
                eq("alice@example.com"), eq("Password1!"), eq("Alice"),
                any(), any(), eq(true), eq(true), eq(false)))
                .thenReturn(savedUser);
        when(userMapper.toDto(savedUser)).thenReturn(new UserResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated());

        verify(registrationService).registerWithEmail(
                eq("alice@example.com"), eq("Password1!"), eq("Alice"),
                any(), any(), eq(true), eq(true), eq(false));
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        when(registrationService.registerWithEmail(
                any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Email already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isBadRequest());
    }

    // ── verifyEmail ───────────────────────────────────────────────────────────

    @Test
    void verifyEmail_validOtp_returns204() throws Exception {
        doNothing().when(registrationService).verifyEmailOtp("alice@example.com", "123456");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"code\":\"123456\",\"type\":\"REGISTER\"}"))
                .andExpect(status().isNoContent());

        verify(registrationService).verifyEmailOtp("alice@example.com", "123456");
    }

    @Test
    void verifyEmail_invalidOtp_returns400() throws Exception {
        doThrow(new InvalidOtpException("Invalid OTP"))
                .when(registrationService).verifyEmailOtp("alice@example.com", "999999");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"code\":\"999999\",\"type\":\"REGISTER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_unknownEmail_returns404() throws Exception {
        doThrow(new UserNotFoundException("User not found"))
                .when(registrationService).verifyEmailOtp("nobody@example.com", "123456");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"code\":\"123456\",\"type\":\"REGISTER\"}"))
                .andExpect(status().isNotFound());
    }

    // ── resendOtp ─────────────────────────────────────────────────────────────

    @Test
    void resendOtp_register_returns204() throws Exception {
        doNothing().when(registrationService)
                .resendOtp("alice@example.com", VerificationType.REGISTER);

        mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"type\":\"REGISTER\"}"))
                .andExpect(status().isNoContent());

        verify(registrationService).resendOtp("alice@example.com", VerificationType.REGISTER);
    }

    @Test
    void resendOtp_passwordReset_returns204() throws Exception {
        doNothing().when(registrationService)
                .resendOtp("alice@example.com", VerificationType.PASSWORD_RESET);

        mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"type\":\"PASSWORD_RESET\"}"))
                .andExpect(status().isNoContent());

        verify(registrationService).resendOtp("alice@example.com", VerificationType.PASSWORD_RESET);
    }

    @Test
    void resendOtp_register_unknownEmail_returns404() throws Exception {
        doThrow(new UserNotFoundException("User not found"))
                .when(registrationService)
                .resendOtp("nobody@example.com", VerificationType.REGISTER);

        mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"type\":\"REGISTER\"}"))
                .andExpect(status().isNotFound());
    }
}
