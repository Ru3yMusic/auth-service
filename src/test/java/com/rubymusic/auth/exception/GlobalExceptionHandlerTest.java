package com.rubymusic.auth.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/test-ex")
        public String throwEx(@RequestParam String type) {
            if ("userNotFound".equals(type))        throw new UserNotFoundException("user not found");
            if ("invalidOtp".equals(type))          throw new InvalidOtpException("invalid otp");
            if ("invalidCredentials".equals(type))  throw new InvalidCredentialsException("invalid credentials");
            if ("emailNotVerified".equals(type))    throw new EmailNotVerifiedException("email not verified");
            if ("rateLimitExceeded".equals(type))   throw new RateLimitExceededException("rate limit exceeded");
            if ("unauthorized".equals(type))        throw new UnauthorizedException("unauthorized");
            if ("forbidden".equals(type))           throw new ForbiddenException("forbidden");
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void userNotFoundException_returns404() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "userNotFound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("user not found"));
    }

    @Test
    void invalidOtpException_returns400() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "invalidOtp"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid otp"));
    }

    @Test
    void invalidCredentialsException_returns401() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "invalidCredentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid credentials"));
    }

    @Test
    void emailNotVerifiedException_returns403() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "emailNotVerified"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("email not verified"));
    }

    @Test
    void rateLimitExceededException_returns429() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "rateLimitExceeded"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("rate limit exceeded"));
    }

    @Test
    void unauthorizedException_returns401() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void forbiddenException_returns403() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }
}
