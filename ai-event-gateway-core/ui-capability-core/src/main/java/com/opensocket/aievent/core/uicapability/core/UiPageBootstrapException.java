package com.opensocket.aievent.core.uicapability.core;

public final class UiPageBootstrapException extends RuntimeException {
    public enum Code { UI_PAGE_CONTEXT_NOT_FOUND, UI_PAGE_CONTRACT_UNSUPPORTED }
    private final Code code;
    public UiPageBootstrapException(Code code, String message) { super(message); this.code = code; }
    public Code code() { return code; }
}
