package com.opensocket.aievent.gateway.netty.api;

/** Success and generic non-error API codes for Netty admin APIs. */
public enum GatewayApiCode {
    OK("OK", "Success");

    private final String code;
    private final String defaultMessage;

    GatewayApiCode(String code, String defaultMessage) {
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
