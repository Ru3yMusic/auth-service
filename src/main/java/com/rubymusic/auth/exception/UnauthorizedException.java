package com.rubymusic.auth.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BaseAuthException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
