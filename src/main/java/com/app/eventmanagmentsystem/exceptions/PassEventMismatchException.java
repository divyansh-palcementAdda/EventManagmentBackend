package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an attendee or gatekeeper attempts to verify or check in a pass
 * against an event that the pass does not belong to.
 */
public class PassEventMismatchException extends AppException {

    public PassEventMismatchException() {
        super("PASS_EVENT_MISMATCH",
                "This pass belongs to a different event and cannot be used here.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public PassEventMismatchException(String message) {
        super("PASS_EVENT_MISMATCH", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

