package com.opensocket.aievent.gateway.netty.authorization;

public class AgentAuthorizationDeniedException extends RuntimeException {
    private final String code;

    public AgentAuthorizationDeniedException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "AGENT_AUTHORIZATION_DENIED" : code;
    }

    public String code() {
        return code;
    }
}
