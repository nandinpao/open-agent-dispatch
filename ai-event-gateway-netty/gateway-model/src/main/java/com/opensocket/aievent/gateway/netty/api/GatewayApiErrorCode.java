package com.opensocket.aievent.gateway.netty.api;

/**
 * Standard error codes for Netty gateway HTTP management APIs.
 *
 * <p>HTTP status is intentionally not part of this API response contract.</p>
 */
public enum GatewayApiErrorCode {
    BAD_REQUEST("BAD_REQUEST", "Bad request."),
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed."),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication is required or invalid."),
    FORBIDDEN("FORBIDDEN", "Access is forbidden."),
    NOT_FOUND("NOT_FOUND", "Resource not found."),
    CONFLICT("CONFLICT", "Resource conflict."),
    INVALID_STATE("INVALID_STATE", "Invalid state."),
    TIMEOUT("TIMEOUT", "Operation timed out."),
    RATE_LIMITED("RATE_LIMITED", "Rate limit exceeded."),
    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "Dependency is unavailable."),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal error."),

    GATEWAY_AGENT_NOT_FOUND("GATEWAY_AGENT_NOT_FOUND", "Agent not found."),
    GATEWAY_AGENT_NOT_CONNECTED("GATEWAY_AGENT_NOT_CONNECTED", "Agent is not connected."),
    GATEWAY_AGENT_DISABLED("GATEWAY_AGENT_DISABLED", "Agent is disabled."),
    GATEWAY_AGENT_REVOKED("GATEWAY_AGENT_REVOKED", "Agent is revoked."),
    GATEWAY_AGENT_AUTH_FAILED("GATEWAY_AGENT_AUTH_FAILED", "Agent authentication failed."),
    GATEWAY_COMMAND_DELIVERY_FAILED("GATEWAY_COMMAND_DELIVERY_FAILED", "Command delivery failed."),
    GATEWAY_COMMAND_TIMEOUT("GATEWAY_COMMAND_TIMEOUT", "Command delivery timed out."),
    GATEWAY_CLUSTER_NODE_NOT_FOUND("GATEWAY_CLUSTER_NODE_NOT_FOUND", "Cluster node not found."),
    GATEWAY_CLUSTER_PEER_UNAVAILABLE("GATEWAY_CLUSTER_PEER_UNAVAILABLE", "Cluster peer is unavailable."),
    GATEWAY_CORE_PROXY_FAILED("GATEWAY_CORE_PROXY_FAILED", "Core proxy request failed."),
    GATEWAY_RUNTIME_DISCONNECT_FAILED("GATEWAY_RUNTIME_DISCONNECT_FAILED", "Runtime disconnect failed.");

    private final String code;
    private final String defaultMessage;

    GatewayApiErrorCode(String code, String defaultMessage) {
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
