package com.rubymusic.auth.exception;

import com.rubymusic.auth.model.enums.BlockReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/test-ex")
        public String throwEx(@RequestParam String type) {
            if ("userNotFound".equals(type))         throw new UserNotFoundException("user not found");
            if ("invalidOtp".equals(type))           throw new InvalidOtpException("invalid otp");
            if ("invalidCredentials".equals(type))   throw new InvalidCredentialsException("invalid credentials");
            if ("emailNotVerified".equals(type))     throw new EmailNotVerifiedException("email not verified");
            if ("rateLimitExceeded".equals(type))    throw new RateLimitExceededException("rate limit exceeded");
            if ("unauthorized".equals(type))         throw new UnauthorizedException("unauthorized");
            if ("forbidden".equals(type))            throw new ForbiddenException("forbidden");
            if ("accountBlocked".equals(type))
                throw new AccountBlockedException("Account is blocked", BlockReason.HARASSMENT_OR_BULLYING);
            if ("accountBlockedNoReason".equals(type))
                throw new AccountBlockedException("Account is blocked", null);
            if ("noSuchElement".equals(type))        throw new NoSuchElementException("not found");
            if ("illegalArgument".equals(type))      throw new IllegalArgumentException("bad arg");
            if ("illegalState".equals(type))         throw new IllegalStateException("bad state");
            if ("notImplemented".equals(type))       throw new UnsupportedOperationException("not impl");
            if ("dataIntegrity".equals(type))        throw new DataIntegrityViolationException("dup");
            if ("generic".equals(type))              throw new RuntimeException("boom");
            return "ok";
        }

        @PostMapping("/validate")
        public String validate(@Valid @RequestBody Body body) {
            return body.value;
        }

        static class Body {
            @NotBlank(message = "value must not be blank")
            public String value;
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── BaseAuthException subclasses ──────────────────────────────────────────

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

    // ── AccountBlockedException (custom handler with blockReason) ─────────────

    @Test
    void accountBlockedException_returns403_withBlockReason() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "accountBlocked"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is blocked"))
                .andExpect(jsonPath("$.blockReason").value("HARASSMENT_OR_BULLYING"));
    }

    @Test
    void accountBlockedException_nullReason_returnsNullBlockReason() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "accountBlockedNoReason"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is blocked"))
                .andExpect(jsonPath("$.blockReason").doesNotExist());
    }

    // ── Generic Spring/Java exceptions ────────────────────────────────────────

    @Test
    void noSuchElementException_returns404() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "noSuchElement"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("not found"))
                .andExpect(jsonPath("$.path").value("/test-ex"));
    }

    @Test
    void illegalArgumentException_returns400() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "illegalArgument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("bad arg"));
    }

    @Test
    void illegalStateException_returns400() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "illegalState"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("bad state"));
    }

    @Test
    void unsupportedOperationException_returns501() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "notImplemented"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.message").value("not impl"));
    }

    @Test
    void dataIntegrityViolation_returns409() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "dataIntegrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Resource already exists"));
    }

    @Test
    void genericException_returns500() throws Exception {
        mockMvc.perform(get("/test-ex").param("type", "generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void validationError_returns422_withFieldMessages() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("value must not be blank"));
    }
}
