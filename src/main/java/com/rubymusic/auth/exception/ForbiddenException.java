package com.rubymusic.auth.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BaseAuthException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
