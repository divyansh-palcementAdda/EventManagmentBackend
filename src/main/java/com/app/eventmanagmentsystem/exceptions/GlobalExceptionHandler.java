package com.app.eventmanagmentsystem.exceptions;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise-grade Global Exception Handler.
 *
 * <p>
 * Centralizes exception translation across all application modules,
 * returning structured, client-friendly responses while preventing
 * disclosure of internal stack traces, SQL syntax, or secrets.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        // ===== 1. Custom Domain Exceptions =====

    @ExceptionHandler(AppException.class)

        public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
                log.warn("Application business exception [{}]: {} on path: {}",
                                ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
                ErrorResponse error = new ErrorResponse(
                                ex.getErrorCode(),
                                ex.getMessage(),
                                ex.getHttpStatus().value(),
                                request.getRequestURI());
                return ResponseEntity.status(ex.getHttpStatus()).body(error);
        }

        // ===== 2. Resource & Endpoint Not Found (Spring 6.1 / Spring Boot 3.2+) =====

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex,
                        HttpServletRequest request) {
                log.warn("Resource/endpoint not found [{}]: {} {}",
                                ex.getResourcePath(), request.getMethod(), request.getRequestURI());
                ErrorResponse error = new ErrorResponse(
                                "RESOURCE_NOT_FOUND",
                                "The requested resource or endpoint was not found: /" + ex.getResourcePath(),
                                HttpStatus.NOT_FOUND.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        @ExceptionHandler(EntityNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex,
                        HttpServletRequest request) {
                log.warn("Entity not found on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "RESOURCE_NOT_FOUND",
                                ex.getMessage() != null ? ex.getMessage() : "Requested entity not found",
                                HttpStatus.NOT_FOUND.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        // ===== 3. Validation & Constraint Violations =====

    @ExceptionHandler(MethodArgumentNotValidException.class)

        public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                        HttpServletRequest request) {
                Map<String, String> fieldErrors = new HashMap<>();
                for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
                        fieldErrors.put(fe.getField(), fe.getDefaultMessage());
                }
                log.warn("Payload validation failed on path {}: {}", request.getRequestURI(), fieldErrors);
                ErrorResponse error = new ErrorResponse(
                                "VALIDATION_FAILED",
                                "Request validation failed for " + fieldErrors.size() + " field(s)",
                                HttpStatus.BAD_REQUEST.value(),
                                request.getRequestURI());
                error.setFieldErrors(fieldErrors);
                return ResponseEntity.badRequest().body(error);
        }

    @ExceptionHandler(ConstraintViolationException.class)

        public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                        HttpServletRequest request) {
                log.warn("Constraint violation on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "CONSTRAINT_VIOLATION",
                                "Constraint validation failed: " + ex.getMessage(),
                                HttpStatus.BAD_REQUEST.value(),
                                request.getRequestURI());
                return ResponseEntity.badRequest().body(error);
        }

        // ===== 4. HTTP Method, Media Type & Parameter Mismatches =====

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                        HttpServletRequest request) {
                String msg = String.format("HTTP method '%s' is not supported for this endpoint. Supported methods: %s",
                                ex.getMethod(), Arrays.toString(ex.getSupportedMethods()));
                log.warn("Method not allowed on path {}: {}", request.getRequestURI(), msg);
                ErrorResponse error = new ErrorResponse(
                                "METHOD_NOT_ALLOWED",
                                msg,
                                HttpStatus.METHOD_NOT_ALLOWED.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
        }

        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                        HttpServletRequest request) {
                log.warn("Unsupported media type on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "UNSUPPORTED_MEDIA_TYPE",
                                ex.getMessage(),
                                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error);
        }

        @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
        public ResponseEntity<ErrorResponse> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                        HttpServletRequest request) {
                log.warn("Media type not acceptable on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "NOT_ACCEPTABLE",
                                "Requested media type cannot be generated",
                                HttpStatus.NOT_ACCEPTABLE.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(error);
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ErrorResponse> handleMissingParams(MissingServletRequestParameterException ex,
                        HttpServletRequest request) {
                String msg = String.format("Required query parameter '%s' of type '%s' is missing",
                                ex.getParameterName(), ex.getParameterType());
                log.warn("Missing parameter on path {}: {}", request.getRequestURI(), msg);
                ErrorResponse error = new ErrorResponse(
                                "MISSING_PARAMETER",
                                msg,
                                HttpStatus.BAD_REQUEST.value(),
                                request.getRequestURI());
                return ResponseEntity.badRequest().body(error);
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                        HttpServletRequest request) {
                String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
                String msg = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s",
                                ex.getValue(), ex.getName(), requiredType);
                log.warn("Type mismatch on path {}: {}", request.getRequestURI(), msg);
                ErrorResponse error = new ErrorResponse(
                                "TYPE_MISMATCH",
                                msg,
                                HttpStatus.BAD_REQUEST.value(),
                                request.getRequestURI());
                return ResponseEntity.badRequest().body(error);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex,
                        HttpServletRequest request) {
                String detailedMsg = "Malformed or unreadable JSON payload in request body.";
                if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                        String causeFirstLine = ex.getCause().getMessage().split("\n")[0];
                        detailedMsg = "Malformed request payload: " + causeFirstLine;
                }
                log.warn("Message not readable on path {}: {}", request.getRequestURI(), detailedMsg);
                ErrorResponse error = new ErrorResponse(
                                "MALFORMED_JSON",
                                detailedMsg,
                                HttpStatus.BAD_REQUEST.value(),
                                request.getRequestURI());
                return ResponseEntity.badRequest().body(error);
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex,
                        HttpServletRequest request) {
                log.warn("Upload size limit exceeded on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "PAYLOAD_TOO_LARGE",
                                "Uploaded file exceeds the maximum permitted size.",
                                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
        }

        // ===== 5. Database Conflicts & Constraints =====

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                        HttpServletRequest request) {
                String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage()
                                : ex.getMessage();
                log.warn("Data integrity conflict on path {}: {}", request.getRequestURI(), detail);
                ErrorResponse error = new ErrorResponse(
                                "DATA_CONFLICT",
                                "Database operation conflicted with existing records or foreign key constraints.",
                                HttpStatus.CONFLICT.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        // ===== 6. Authentication & Security Exceptions =====

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex,
                        HttpServletRequest request) {
                log.warn("Bad credentials on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "INVALID_CREDENTIALS",
                                "Invalid email or password",
                                HttpStatus.UNAUTHORIZED.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        @ExceptionHandler({ DisabledException.class, LockedException.class })
        public ResponseEntity<ErrorResponse> handleDisabledOrLocked(AuthenticationException ex,
                        HttpServletRequest request) {
                log.warn("Account disabled/locked on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "ACCOUNT_DISABLED",
                                "Your account has been deactivated, suspended, or locked. Please contact your system administrator.",
                                HttpStatus.FORBIDDEN.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

    @ExceptionHandler(AuthenticationException.class)

        public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
                        HttpServletRequest request) {
                log.warn("Authentication failed on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "UNAUTHENTICATED",
                                "Authentication required to access this resource",
                                HttpStatus.UNAUTHORIZED.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

    @ExceptionHandler(AccessDeniedException.class)

        public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
                log.warn("Access denied on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "ACCESS_DENIED",
                                "You do not possess the required permissions to perform this action.",
                                HttpStatus.FORBIDDEN.value(),
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // ===== 7. General Client Arguments & State =====

        @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
        public ResponseEntity<ErrorResponse> handleIllegalArguments(RuntimeException ex, HttpServletRequest request) {
                log.warn("Illegal argument or state on path {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse error = new ErrorResponse(
                                "INVALID_REQUEST",
                                ex.getMessage() != null ? ex.getMessage() : "Invalid request parameters",
                                HttpStatus.BAD_REQUEST.value(),
                                request.getRequestURI());
                return ResponseEntity.badRequest().body(error);
        }

        // ===== 8. Fallback Unhandled Exception =====

    @ExceptionHandler(Exception.class)

        public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
                String correlationId = UUID.randomUUID().toString();
                log.error("Unhandled internal server error [correlationId={}, path={}]: {}",
                                correlationId, request.getRequestURI(), ex.getMessage(), ex);
                ErrorResponse error = new ErrorResponse(
                                "INTERNAL_ERROR",
                                "An unexpected internal error occurred. Reference correlation ID: " + correlationId,
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                request.getRequestURI(),
                                correlationId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
}
