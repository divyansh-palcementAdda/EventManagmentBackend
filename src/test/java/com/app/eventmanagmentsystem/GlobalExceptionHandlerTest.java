package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.AppException;
import com.app.eventmanagmentsystem.exceptions.ErrorResponse;
import com.app.eventmanagmentsystem.exceptions.GlobalExceptionHandler;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        request.setMethod("GET");
    }

    @Test
    @DisplayName("Handle NoResourceFoundException (Spring 6.1 static resource / endpoint missing) -> 404")
    void testHandleNoResourceFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "api/v1");

        var response = exceptionHandler.handleNoResourceFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).contains("api/v1");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    @Test
    @DisplayName("Handle AppException (domain business exceptions) -> configured status and code")
    void testHandleAppException() {
        AppException ex = new ResourceNotFoundException("Pass", "PASS-12345");

        var response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("PASS_NOT_FOUND");
        assertThat(response.getBody().getMessage()).contains("PASS-12345");
    }

    @Test
    @DisplayName("Handle HttpRequestMethodNotSupportedException -> 405")
    void testHandleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET", "DELETE"));

        var response = exceptionHandler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getBody().getMessage()).contains("POST");
    }

    @Test
    @DisplayName("Handle BadCredentialsException -> 401")
    void testHandleBadCredentials() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        var response = exceptionHandler.handleBadCredentials(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("Handle AccessDeniedException -> 403")
    void testHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        var response = exceptionHandler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("Handle generic fallback Exception -> 500 with correlationId")
    void testHandleGenericException() {
        RuntimeException ex = new RuntimeException("Unexpected internal failure");

        var response = exceptionHandler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getCorrelationId()).isNotBlank();
        assertThat(response.getBody().getMessage()).contains("Reference correlation ID:");
    }
}
