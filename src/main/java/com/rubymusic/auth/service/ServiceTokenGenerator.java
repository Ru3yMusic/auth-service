package com.rubymusic.auth.service;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.util.Date;
import java.util.Map;

/**
 * Generates short-lived service JWTs for zero-trust M2M authentication.
 * Only auth-service holds the RSA private key, so only it can issue service tokens.
 */
@Component
@RequiredArgsConstructor
public class ServiceTokenGenerator {

    public static final long SERVICE_TOKEN_TTL_MS = 3_600_000L; // 1 hour

    private final PrivateKey jwtPrivateKey;

    public String generateServiceToken(String serviceName) {
        return Jwts.builder()
                .subject(serviceName)
                .claims(Map.of("role", "SERVICE", "iss", "ruby-music-internal"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + SERVICE_TOKEN_TTL_MS))
                .signWith(jwtPrivateKey)
                .compact();
    }
}
