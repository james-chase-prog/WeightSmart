package com.weightsmart.server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/*
 * GlobalExceptionHandler
 * Converts common exceptions into clean JSON error responses for the Android client.
 *
 * Architecture Role:
 * Instead of sending 50 pages of Java Stack Trace logs to the Android App (which is insecure
 * and useless), this class intercepts errors and returns clean, simple JSON objects
 * with field-specific or general error messages.
 *
 * It uses AOP (Aspect Oriented Programming) via @RestControllerAdvice to "watch" every
 * Controller. When an exception is thrown inside any @RestController method, Spring
 * routes it here BEFORE the default error handler generates a stack trace response.
 *
 * Key Concepts & Documentation:
 * @RestControllerAdvice: AOP advice that intercepts exceptions from all @RestController classes.
 * <a href="https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html">Reference: Controller Advice</a>
 * @ExceptionHandler: Maps specific exception types to handler methods.
 * <a href="https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html">Reference: @ExceptionHandler</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-14
 */
//FIXME: As possible errors are uncovered this file will need further development
@RestControllerAdvice
public class GlobalExceptionHandler {

    // --- VALIDATION ERRORS (e.g. "Future Date", "Empty Password") ---

    /**
     * Handles @Valid annotation failures from DTOs.
     * Extracts field-specific error messages and returns them as a JSON map.
     * Example response: {"date": "must be a date in the past or in the present", "weight": "must be positive"}
     *
     * @param ex The validation exception containing all field errors.
     * @return 400 Bad Request with a map of field names to error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        // Extract field-specific error messages
        // e.g., "date": "must be a date in the past or in the present"
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    // --- BUSINESS LOGIC ERRORS (e.g. "Username taken") ---

    /**
     * Catches IllegalArgumentException thrown by service-layer validation
     * (e.g., "Username or Email already exists" from UserService.registerUser()).
     *
     * @param ex The business logic exception with a user-friendly message.
     * @return 400 Bad Request with {"error": "message"}.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBusinessLogicErrors(IllegalArgumentException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // --- SECURITY ERRORS (e.g. "Wrong Password") ---

    /**
     * Catches BadCredentialsException from Spring Security's AuthenticationManager.
     * Returns a generic "Invalid username or password" message to prevent username enumeration.
     *
     * @param ex The authentication failure exception.
     * @return 401 Unauthorized with a generic error message.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Invalid username or password");
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    // --- CATCH-ALL (Unexpected Crashes) ---

    /**
     * Safety net for any unhandled exceptions.
     * Returns a vague message to prevent leaking internal stack traces, class names,
     * or SQL errors to the client (which would aid attackers in reconnaissance).
     *
     * @param ex Any uncaught exception.
     * @return 500 Internal Server Error with a generic error message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "An unexpected error occurred");
        // In Dev, you might print the real message, but in Prod, keep it vague.
        errorResponse.put("details", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
