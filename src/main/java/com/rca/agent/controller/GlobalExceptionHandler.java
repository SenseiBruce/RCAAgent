package com.rca.agent.controller;

import com.rca.agent.fix.AutoFixException;
import com.rca.agent.service.RcaException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler providing consistent error responses across all controllers.
 *
 * <p>Maps exceptions to appropriate HTTP status codes and returns structured error payloads
 * containing a message and timestamp.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return ResponseEntity.badRequest().body(errorBody(message));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(errorBody(ex.getMessage(), "IllegalArgument"));
  }

  @ExceptionHandler(RcaException.class)
  public ResponseEntity<Map<String, Object>> handleRca(RcaException ex) {
    log.warn("RCA failed: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(errorBody(ex.getMessage(), ex.getClass().getSimpleName()));
  }

  @ExceptionHandler(AutoFixException.class)
  public ResponseEntity<Map<String, Object>> handleAutoFix(AutoFixException ex) {
    log.warn("Auto-fix failed: {}", ex.getMessage());
    HttpStatus status =
        ex instanceof AutoFixException.UnsupportedPlatform
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.UNPROCESSABLE_ENTITY;
    return ResponseEntity.status(status)
        .body(errorBody(ex.getMessage(), ex.getClass().getSimpleName()));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(
      org.springframework.web.servlet.resource.NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(errorBody("Not found: " + ex.getResourcePath(), "NotFound"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(errorBody("Internal error: " + ex.getMessage(), "InternalError"));
  }

  private Map<String, Object> errorBody(String message) {
    return errorBody(message, "Error");
  }

  private Map<String, Object> errorBody(String message, String code) {
    return Map.of("error", message, "code", code, "timestamp", Instant.now().toString());
  }
}
