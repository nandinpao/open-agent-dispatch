package com.opensocket.aievent.core.iam.runtime.eventintake;

/** Fail-closed resource-server error with stable external reason code. */
public final class EventIntakeResourceAuthorizationException extends RuntimeException {
    private final int httpStatus;
    private final String reasonCode;

    public EventIntakeResourceAuthorizationException(int httpStatus, String reasonCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.reasonCode = reasonCode;
    }
    public int httpStatus() { return httpStatus; }
    public String reasonCode() { return reasonCode; }

    public static EventIntakeResourceAuthorizationException unauthorized(String code, String message) {
        return new EventIntakeResourceAuthorizationException(401, code, message);
    }
    public static EventIntakeResourceAuthorizationException forbidden(String code, String message) {
        return new EventIntakeResourceAuthorizationException(403, code, message);
    }
    public static EventIntakeResourceAuthorizationException unavailable(String code, String message) {
        return new EventIntakeResourceAuthorizationException(503, code, message);
    }
    public static EventIntakeResourceAuthorizationException rateLimited() {
        return new EventIntakeResourceAuthorizationException(429, "MACHINE_EVENT_INTAKE_RATE_LIMITED", "Event Intake rate limit exceeded.");
    }
}
