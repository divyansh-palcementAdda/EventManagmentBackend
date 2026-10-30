package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

public class EventAccessDeniedException extends AppException {
    public EventAccessDeniedException() {
        super("EVENT_ACCESS_DENIED", "You are not authorized to perform this action for this event.", HttpStatus.FORBIDDEN);
    }
}

