package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends AppException {
    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}

