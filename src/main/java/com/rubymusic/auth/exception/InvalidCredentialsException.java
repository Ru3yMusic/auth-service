package com.rubymusic.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends BaseAuthException {

    public InvalidCredentialsException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
