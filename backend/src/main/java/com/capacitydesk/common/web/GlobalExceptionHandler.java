package com.capacitydesk.common.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> domain(DomainException ex, WebRequest req) {
        return body(ex.status(), ex.code(), ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> unauthenticated(AuthenticationException ex, WebRequest req) {
        return body(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required or invalid credentials", req, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> denied(AccessDeniedException ex, WebRequest req) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", req, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> integrity(DataIntegrityViolationException ex, WebRequest req) {
        log.warn("Integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "DATA_CONFLICT", "Request conflicts with existing data", req, List.of());
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    ResponseEntity<Object> concurrent(org.springframework.dao.OptimisticLockingFailureException ex, WebRequest req) {
        return body(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Resource was modified concurrently; retry", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, WebRequest req) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", req, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .sorted()
                .toList();
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", req, details);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        List<String> details = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(e -> {
                    String name = e instanceof FieldError f ? f.getField() : String.valueOf(r.getMethodParameter().getParameterName());
                    return name + ": " + e.getDefaultMessage();
                }))
                .sorted()
                .toList();
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", req, details);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        HttpStatus http = HttpStatus.valueOf(status.value());
        String msg = http == HttpStatus.BAD_REQUEST ? "Malformed request" : http.getReasonPhrase();
        return body(http, "HTTP_" + http.value(), msg, req, List.of());
    }

    private static ResponseEntity<Object> body(HttpStatus status, String code, String msg, WebRequest req, List<String> details) {
        String path = req instanceof ServletWebRequest s ? s.getRequest().getRequestURI() : "";
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, msg, path, details));
    }
}
