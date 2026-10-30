package com.app.eventmanagmentsystem.exceptions;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resourceType, String identifier) {
        super(resourceType.toUpperCase() + "_NOT_FOUND",
              resourceType + " not found: " + identifier,
              HttpStatus.NOT_FOUND);
    }
}

