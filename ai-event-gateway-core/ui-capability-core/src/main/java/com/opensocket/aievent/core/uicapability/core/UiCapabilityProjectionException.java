package com.opensocket.aievent.core.uicapability.core;

public final class UiCapabilityProjectionException extends RuntimeException {
    private final Code code;
    public UiCapabilityProjectionException(Code code, String message) {
        super(message);
        this.code = code;
    }
    public Code code() { return code; }
    public enum Code {
        UI_CAPABILITY_CONTRACT_UNSUPPORTED,
        UI_CAPABILITY_CONTEXT_INVALID,
        UI_CAPABILITY_RESOURCE_TYPE_MISMATCH,
        UI_CAPABILITY_PRINCIPAL_EPOCH_STALE,
        UI_CAPABILITY_BATCH_LIMIT_EXCEEDED,
        UI_CAPABILITY_PAYLOAD_TOO_LARGE
    }
}
