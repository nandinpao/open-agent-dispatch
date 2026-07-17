package com.opensocket.aievent.gateway.netty.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Standard API envelope for Netty gateway management APIs.
 *
 * <p>P19-E aligns Netty admin APIs with the OpenDispatch standard HTTP
 * envelope: code/message/data/timestamp. Business errors are represented by
 * code while HTTP status is kept at 200 for management API responses.</p>
 */
public record GatewayApiResponse<T>(
        String code,
        String message,
        T data,
        OffsetDateTime timestamp
) {
    public static <T> GatewayApiResponse<T> ok(T data) {
        return new GatewayApiResponse<>(
                GatewayApiCode.OK.code(),
                GatewayApiCode.OK.defaultMessage(),
                data,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static GatewayApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> GatewayApiResponse<T> error(String code, String message) {
        return new GatewayApiResponse<>(
                normalizeCode(code),
                normalizeMessage(message),
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static <T> GatewayApiResponse<T> error(GatewayApiErrorCode code) {
        return error(code.code(), code.defaultMessage());
    }

    public static <T> GatewayApiResponse<T> error(GatewayApiErrorCode code, String message) {
        return error(code.code(), message == null || message.isBlank() ? code.defaultMessage() : message);
    }

    private static String normalizeCode(String code) {
        return code == null || code.isBlank() ? GatewayApiErrorCode.INTERNAL_ERROR.code() : code;
    }

    private static String normalizeMessage(String message) {
        return message == null || message.isBlank() ? GatewayApiErrorCode.INTERNAL_ERROR.defaultMessage() : message;
    }
}
