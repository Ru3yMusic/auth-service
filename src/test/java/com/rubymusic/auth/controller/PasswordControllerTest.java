package com.rubymusic.auth.controller;

import com.rubymusic.auth.exception.GlobalExceptionHandler;
import com.rubymusic.auth.exception.InvalidOtpException;
import com.rubymusic.auth.exception.UserNotFoundException;
import com.rubymusic.auth.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PasswordControllerTest {

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private PasswordController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── requestPasswordReset ──────────────────────────────────────────────────

    @Test
    void requestPasswordReset_validEmail_returns204() throws Exception {
        doNothing().when(passwordResetService).requestPasswordReset("a@b.com");

        mockMvc.perform(post("/api/v1/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isNoContent());

        verify(passwordResetService).requestPasswordReset("a@b.com");
    }

    @Test
    void requestPasswordReset_unknownEmail_stillReturns204_silentByDesign() throws Exception {
        // Service silences unknown-email branch — controller returns 204 either way
        doNothing().when(passwordResetService).requestPasswordReset("nobody@b.com");

        mockMvc.perform(post("/api/v1/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@b.com\"}"))
                .andExpect(status().isNoContent());
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_validRequest_returns204() throws Exception {
        doNothing().when(passwordResetService)
                .resetPassword("a@b.com", "123456", "NewPass1!");

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"code\":\"123456\",\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isNoContent());

        verify(passwordResetService).resetPassword("a@b.com", "123456", "NewPass1!");
    }

    @Test
    void resetPassword_invalidOtp_returns400() throws Exception {
        doThrow(new InvalidOtpException("Invalid OTP"))
                .when(passwordResetService)
                .resetPassword("a@b.com", "999999", "NewPass1!");

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"code\":\"999999\",\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_unknownEmail_returns404() throws Exception {
        doThrow(new UserNotFoundException("User not found"))
                .when(passwordResetService)
                .resetPassword("nobody@b.com", "123456", "NewPass1!");

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@b.com\",\"code\":\"123456\",\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isNotFound());

        verify(passwordResetService, never()).resetPassword("a@b.com", "123456", "NewPass1!");
    }
}
