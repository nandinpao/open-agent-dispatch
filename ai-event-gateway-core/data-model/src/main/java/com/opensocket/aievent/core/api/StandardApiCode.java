package com.opensocket.aievent.core.api;

/** Success and generic non-error API codes. */
public enum StandardApiCode {
    OK("OK", "Success");

    private final String code;
    private final String defaultMessage;

    StandardApiCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
