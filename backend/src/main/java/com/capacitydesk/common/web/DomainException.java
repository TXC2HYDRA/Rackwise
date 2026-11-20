package com.capacitydesk.common.web;

import org.springframework.http.HttpStatus;

/** Business-rule failure mapped to an HTTP status by {@link GlobalExceptionHandler}. */
public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DomainException notFound(String what) {
        return new DomainException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    public static DomainException forbidden(String code, String message) {
        return new DomainException(HttpStatus.FORBIDDEN, code, message);
    }

    public static DomainException unprocessable(String code, String message) {
        return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
