package com.rubymusic.auth.service;

/**
 * Handles email + password credential verification and delegates
 * token issuance to {@link TokenService}.
 */
public interface LoginService {

    /**
     * Verifies the given credentials and issues a JWT access + refresh token pair.
     *
     * @throws com.rubymusic.auth.exception.InvalidCredentialsException if the email is not found
     *                                                                   or the password does not match
     * @throws com.rubymusic.auth.exception.EmailNotVerifiedException   if the user's email has not
     *                                                                   been verified
     */
    TokenPair login(String email, String password, String deviceInfo);
}
