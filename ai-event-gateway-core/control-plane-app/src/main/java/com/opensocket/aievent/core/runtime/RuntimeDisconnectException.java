package com.opensocket.aievent.core.runtime;

public class RuntimeDisconnectException extends RuntimeException {
    private final RuntimeDisconnectResult result;

    public RuntimeDisconnectException(RuntimeDisconnectResult result) {
        super(result == null ? "Runtime disconnect failed" : result.message());
        this.result = result;
    }

    public RuntimeDisconnectResult getResult() { return result; }
}
