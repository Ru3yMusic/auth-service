package com.rubymusic.auth.service;

/**
 * Carries both the short-lived access token and the long-lived refresh token
 * after a successful login or token refresh.
 */
public record TokenPair(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInMs
) {}
