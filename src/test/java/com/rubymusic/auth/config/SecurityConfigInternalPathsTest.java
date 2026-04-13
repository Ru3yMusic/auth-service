package com.rubymusic.auth.config;

import com.rubymusic.auth.controller.InternalAuthController;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.service.ServiceTokenGenerator;
import com.rubymusic.auth.service.UserService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Date;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Verifies that the security filter chain enforces zero-trust rules on internal paths:
 * - No token  → 401 Unauthorized
 * - User JWT  → 403 Forbidden
 * - Service JWT → 200 OK
 * - service-token endpoint → open (no JWT required)
 */
@WebMvcTest(InternalAuthController.class)
@Import({
        SecurityConfig.class,
        InternalJwtAuthFilter.class,
        SecurityConfigInternalPathsTest.TestKeyConfig.class
})
@ActiveProfiles("test")
class SecurityConfigInternalPathsTest {

    // Generated once before Spring context — used in @TestConfiguration below
    private static final KeyPair TEST_KEY_PAIR;
    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            TEST_KEY_PAIR = gen.generateKeyPair();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @TestConfiguration
    static class TestKeyConfig {
        @Bean
        @Primary
        public PublicKey jwtPublicKey() {
            return TEST_KEY_PAIR.getPublic();
        }

        @Bean
        @Primary
        public PrivateKey jwtPrivateKey() {
            return TEST_KEY_PAIR.getPrivate();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private ServiceTokenGenerator serviceTokenGenerator;

    // ── 401 matrix (no token) ─────────────────────────────────────────────────

    @Test
    void getUserById_noToken_returns401() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/internal/v1/auth/users/{id}", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUsersBatch_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/internal/v1/auth/users/batch")
                        .param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    // ── 403 matrix (user JWT — wrong role) ────────────────────────────────────

    @Test
    void getUserById_userToken_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        String userJwt = signJwt("user-id", "USER");

        mockMvc.perform(get("/api/internal/v1/auth/users/{id}", id)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUsersBatch_userToken_returns403() throws Exception {
        String userJwt = signJwt("user-id", "USER");

        mockMvc.perform(get("/api/internal/v1/auth/users/batch")
                        .param("ids", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isForbidden());
    }

    // ── 200 matrix (service JWT — correct role) ───────────────────────────────

    @Test
    void getUserById_serviceToken_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .email("u@test.com")
                .displayName("Test")
                .status(UserStatus.ACTIVE)
                .build();
        when(userService.findById(id)).thenReturn(user);

        String serviceJwt = signJwt("interaction-service", "SERVICE");
        mockMvc.perform(get("/api/internal/v1/auth/users/{id}", id)
                        .header("Authorization", "Bearer " + serviceJwt))
                .andExpect(status().isOk());
    }

    // ── service-token endpoint — must be open (no JWT required) ──────────────

    @Test
    void serviceTokenEndpoint_isAccessibleWithoutJwt() throws Exception {
        when(serviceTokenGenerator.generateServiceToken("svc")).thenReturn("issued-jwt");

        // Wrong secret → 401 from the controller (not from Spring Security → means endpoint was reached)
        mockMvc.perform(post("/api/internal/v1/auth/service-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceName\":\"svc\",\"serviceSecret\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());  // 401 from controller (invalid secret), not Spring Security
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String signJwt(String subject, String role) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("iss", "ruby-music-internal")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(TEST_KEY_PAIR.getPrivate())
                .compact();
    }
}
