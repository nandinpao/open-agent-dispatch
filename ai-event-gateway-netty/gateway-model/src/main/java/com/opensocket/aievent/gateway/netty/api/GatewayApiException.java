package com.opensocket.aievent.gateway.netty.api;

/** Runtime exception carrying a standard Netty gateway API code. */
public class GatewayApiException extends RuntimeException {
    private final String code;

    public GatewayApiException(GatewayApiErrorCode code) {
        this(code.code(), code.defaultMessage());
    }

    public GatewayApiException(GatewayApiErrorCode code, String message) {
        this(code.code(), message == null || message.isBlank() ? code.defaultMessage() : message);
    }

    public GatewayApiException(String code, String message) {
        super(message == null || message.isBlank() ? GatewayApiErrorCode.INTERNAL_ERROR.defaultMessage() : message);
        this.code = code == null || code.isBlank() ? GatewayApiErrorCode.INTERNAL_ERROR.code() : code;
    }

    public String getCode() {
        return code;
    }
}
