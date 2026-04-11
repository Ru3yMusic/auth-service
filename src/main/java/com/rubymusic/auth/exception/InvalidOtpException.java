package com.rubymusic.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidOtpException extends BaseAuthException {

    public InvalidOtpException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
