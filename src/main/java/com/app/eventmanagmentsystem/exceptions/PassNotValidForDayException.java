package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

public class PassNotValidForDayException extends AppException {
    public PassNotValidForDayException(String message) {
        super("PASS_NOT_VALID_FOR_DAY", message, HttpStatus.FORBIDDEN);
    }
}

