package com.rubymusic.auth.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BaseAuthException {

    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
