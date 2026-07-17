package com.opensocket.aievent.gateway.netty.outbound;

import java.time.Duration;

public record CoreOutboundResult(
        CoreOutboundStatus status,
        int httpStatus,
        String responseBody,
        String message,
        Duration duration,
        Throwable error
) {
    public boolean success2xx() {
        return status == CoreOutboundStatus.SUCCEEDED && httpStatus >= 200 && httpStatus < 300;
    }

    public static CoreOutboundResult completed(int httpStatus, String responseBody, Duration duration) {
        var status = httpStatus >= 200 && httpStatus < 300 ? CoreOutboundStatus.SUCCEEDED : CoreOutboundStatus.HTTP_ERROR;
        return new CoreOutboundResult(status, httpStatus, responseBody, "HTTP " + httpStatus, duration, null);
    }

    public static CoreOutboundResult timeout(String message, Duration duration, Throwable error) {
        return new CoreOutboundResult(CoreOutboundStatus.TIMEOUT, 0, "", message, duration, error);
    }

    public static CoreOutboundResult interrupted(Duration duration, Throwable error) {
        return new CoreOutboundResult(CoreOutboundStatus.INTERRUPTED, 0, "", "Core outbound request interrupted", duration, error);
    }

    public static CoreOutboundResult failed(String message, Duration duration, Throwable error) {
        return new CoreOutboundResult(CoreOutboundStatus.FAILED, 0, "", message, duration, error);
    }
}
