package com.opensocket.aievent.core.enforcement.activation.application;

public final class CutoverPlanException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    public CutoverPlanException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
