package com.opensocket.aievent.core.enforcement.activation.application;

public final class Wave0ReadPilotException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;

    public Wave0ReadPilotException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "WAVE0_READ_PILOT_FAILED" : code.trim();
    }

    public String code() { return code; }
}
