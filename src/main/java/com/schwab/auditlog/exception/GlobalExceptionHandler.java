package com.schwab.auditlog.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Validation failed");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AuditLogExceptions.InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(AuditLogExceptions.InvalidRequestException ex) {
        return ResponseEntity.badRequest().body(baseBody(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(AuditLogExceptions.AppendTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleAppendTimeout(AuditLogExceptions.AppendTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(baseBody(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()));
    }

    @ExceptionHandler(AuditLogExceptions.ChainIntegrityException.class)
    public ResponseEntity<Map<String, Object>> handleChainIntegrity(AuditLogExceptions.ChainIntegrityException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(baseBody(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(baseBody(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + ex.getMessage()));
    }

    private Map<String, Object> baseBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}