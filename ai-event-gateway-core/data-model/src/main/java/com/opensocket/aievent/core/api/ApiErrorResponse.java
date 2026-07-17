package com.opensocket.aievent.core.api;

import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

import com.opensocket.aievent.core.kernel.CoreTime;

@Deprecated(since = "P19-A", forRemoval = false)
@Getter
@Setter
public class ApiErrorResponse {
    private String errorCode;
    private int httpStatus;
    private boolean retryable;
    private String message;
    private OffsetDateTime timestamp = CoreTime.nowUtc();

    public ApiErrorResponse() {}

    public ApiErrorResponse(String errorCode, int httpStatus, boolean retryable, String message) {
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.message = message;
    }
}
