package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

public class PassNotActivatedException extends AppException {
    public PassNotActivatedException() {
        super("PASS_NOT_ACTIVATED", "This pass has not been activated yet.", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

