package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

public class PassAlreadyCheckedInException extends AppException {
    public PassAlreadyCheckedInException() {
        super("PASS_ALREADY_CHECKED_IN", "This pass has already been checked in.", HttpStatus.CONFLICT);
    }
}

