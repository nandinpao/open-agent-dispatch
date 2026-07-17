package com.opensocket.aievent.core.api;

/** Runtime exception that carries an API response code independent from HTTP status. */
public class StandardApiException extends RuntimeException {
    private final String code;

    public StandardApiException(StandardApiErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public StandardApiException(StandardApiErrorCode code, String message) {
        this(code, message, null);
    }

    public StandardApiException(StandardApiErrorCode code, String message, Throwable cause) {
        super(message == null || message.isBlank() ? code.defaultMessage() : message, cause);
        this.code = code.code();
    }

    public StandardApiException(String code, String message) {
        this(code, message, null);
    }

    public StandardApiException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null || code.isBlank() ? StandardApiErrorCode.INTERNAL_ERROR.code() : code;
    }

    public String getCode() {
        return code;
    }
}
