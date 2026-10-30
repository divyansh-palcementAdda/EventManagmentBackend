package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidQrTokenException extends AppException {
    public InvalidQrTokenException() {
        super("INVALID_QR_TOKEN", "The provided QR token is invalid.", HttpStatus.UNAUTHORIZED);
    }
}

