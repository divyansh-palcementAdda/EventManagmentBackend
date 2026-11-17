package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class InvalidRequestException extends AppException {

    private final Map<String, String> fieldErrors;

    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
        this.fieldErrors = Collections.emptyMap();
    }

    public InvalidRequestException(String field, String message) {
        super("INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
        this.fieldErrors = Map.of(field, message);
    }

    public InvalidRequestException(String message, Map<String, String> fieldErrors) {
        super("INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyMap();
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
