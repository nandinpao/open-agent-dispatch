package com.opensocket.aievent.core.uiaccessrequest.application;

public final class GovernedAccessRequestException extends RuntimeException {
    public enum Code {
        ACCESS_REQUEST_ACTION_NOT_REQUESTABLE,
        ACCESS_REQUEST_RESOURCE_NOT_FOUND,
        ACCESS_REQUEST_RESOURCE_VERSION_STALE,
        ACCESS_REQUEST_VISIBILITY_NOT_ALLOWED,
        ACCESS_REQUEST_DURATION_NOT_ALLOWED,
        ACCESS_REQUEST_NOT_FOUND,
        ACCESS_REQUEST_INVALID_STATE,
        ACCESS_REQUEST_VERSION_CONFLICT,
        ACCESS_REQUEST_SEPARATION_OF_DUTIES,
        ACCESS_REQUEST_APPROVER_NOT_AUTHORIZED,
        ACCESS_REQUEST_GRANT_DRIFT
    }
    private final Code code;
    public GovernedAccessRequestException(Code code, String message) { super(message); this.code = code; }
    public Code code() { return code; }
}
