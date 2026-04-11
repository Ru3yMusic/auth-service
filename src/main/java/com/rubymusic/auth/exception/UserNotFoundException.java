package com.rubymusic.auth.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BaseAuthException {

    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
