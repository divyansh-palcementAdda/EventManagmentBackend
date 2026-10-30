package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

public class PassRevokedException extends AppException {
    public PassRevokedException() {
        super("PASS_REVOKED", "This pass has been revoked.", HttpStatus.GONE);
    }
}

