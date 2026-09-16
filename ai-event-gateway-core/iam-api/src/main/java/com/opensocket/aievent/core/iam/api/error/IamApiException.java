package com.opensocket.aievent.core.iam.api.error;

import org.springframework.http.HttpStatus;

public final class IamApiException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final String requiredPermission;

    public IamApiException(HttpStatus status, String code, String message, String permission) {
        super(message);
        this.status = status;
        this.errorCode = code;
        this.requiredPermission = permission == null ? "" : permission;
    }

    public static IamApiException badRequest(String code, String message) {
        return new IamApiException(HttpStatus.BAD_REQUEST, code, message, "");
    }

    public static IamApiException unauthorized(String code, String message) {
        return new IamApiException(HttpStatus.UNAUTHORIZED, code, message, "");
    }

    public static IamApiException notFound(String code, String message) {
        return new IamApiException(HttpStatus.NOT_FOUND, code, message, "");
    }

    public static IamApiException conflict(String code, String message) {
        return new IamApiException(HttpStatus.CONFLICT, code, message, "");
    }

    public static IamApiException preconditionRequired(String code, String message) {
        return new IamApiException(HttpStatus.PRECONDITION_REQUIRED, code, message, "");
    }

    public static IamApiException forbidden(String code, String message, String permission) {
        return new IamApiException(HttpStatus.FORBIDDEN, code, message, permission);
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public String requiredPermission() {
        return requiredPermission;
    }
}
