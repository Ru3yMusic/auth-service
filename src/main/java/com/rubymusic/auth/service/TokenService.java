package com.rubymusic.auth.service;

import com.rubymusic.auth.model.User;

import java.util.UUID;

/**
 * Manages the full lifecycle of JWTs and refresh tokens:
 * generation, rotation, logout, and theft detection.
 */
public interface TokenService {

    /** Generates a signed, short-lived JWT access token for the given user. */
    String generateAccessToken(User user);

    /**
     * Issues a new access + refresh token pair, saves the hashed refresh token
     * to the database, and returns both tokens to the caller.
     */
    TokenPair issueTokenPair(User user, String deviceInfo);

    /**
     * Rotates the refresh token: revokes the current one and issues a new pair.
     * If the submitted token is already revoked, ALL sessions for the owner are
     * revoked (token theft detection) and an {@link com.rubymusic.auth.exception.UnauthorizedException}
     * is thrown.
     */
    TokenPair refreshAccessToken(String rawRefreshToken);

    /** Revokes the single session identified by the given raw refresh token. */
    void logout(String rawRefreshToken);

    /** Revokes ALL active sessions for the given user (e.g. password change, account block). */
    void logoutAll(UUID userId);
}
