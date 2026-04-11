package com.rubymusic.auth.exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends BaseAuthException {

    public EmailNotVerifiedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
