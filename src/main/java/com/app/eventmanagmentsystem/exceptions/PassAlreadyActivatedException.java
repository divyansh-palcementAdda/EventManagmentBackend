package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

public class PassAlreadyActivatedException extends AppException {
    public PassAlreadyActivatedException() {
        super("PASS_ALREADY_ACTIVATED", "This pass has already been activated.", HttpStatus.CONFLICT);
    }
}

