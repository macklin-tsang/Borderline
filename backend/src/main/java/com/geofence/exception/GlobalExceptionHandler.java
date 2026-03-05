package com.geofence.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setDetail(detail);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad Request");
        problem.setDetail(ex.getMessage());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Authentication Failed");
        problem.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailExists(EmailAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Email Already Registered");
        // Intentionally vague: do not echo the email to avoid confirming registration
        problem.setDetail("An account with that email address already exists");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(GeofenceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleGeofenceNotFound(GeofenceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Geofence Not Found");
        problem.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleDeviceNotFound(DeviceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Device Not Found");
        problem.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // Catch-all: prevents stack traces leaking to clients on unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
