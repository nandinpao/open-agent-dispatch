package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;

import com.opensocket.aievent.core.kernel.CoreTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Standard API envelope for OpenDispatch HTTP management/business APIs.
 *
 * <p>P19 introduces the standard envelope for normal and error responses
 * across OpenDispatch HTTP management/business APIs.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StandardApiResponse<T> {
    private String code;
    private String message;
    private T data;
    private OffsetDateTime timestamp;

    public static <T> StandardApiResponse<T> ok(T data) {
        return new StandardApiResponse<>(StandardApiCode.OK.code(), StandardApiCode.OK.defaultMessage(), data, CoreTime.nowUtc());
    }

    public static StandardApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> StandardApiResponse<T> error(String code, String message) {
        return new StandardApiResponse<>(code, normalizeMessage(message), null, CoreTime.nowUtc());
    }

    public static <T> StandardApiResponse<T> error(StandardApiErrorCode code) {
        return error(code.code(), code.defaultMessage());
    }

    public static <T> StandardApiResponse<T> error(StandardApiErrorCode code, String message) {
        return error(code.code(), message == null || message.isBlank() ? code.defaultMessage() : message);
    }

    private static String normalizeMessage(String message) {
        return message == null || message.isBlank() ? StandardApiErrorCode.INTERNAL_ERROR.defaultMessage() : message;
    }
}
