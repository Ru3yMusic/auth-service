package com.rubymusic.auth.dto;

/**
 * Returned by POST /api/v1/auth/internal/service-token.
 * Used by other microservices to obtain a service JWT for M2M calls.
 */
public record ServiceTokenResponse(String token, long expiresIn) {}
