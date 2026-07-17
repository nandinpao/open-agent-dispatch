package com.opensocket.aievent.core.api;

/**
 * Standard error codes for Core HTTP management/business APIs.
 *
 * <p>Stage 8-F0f restores HTTP semantics for error envelopes: clients still receive
 * the stable {@code code/message/data/timestamp} body, but non-OK envelopes must
 * no longer be hidden behind HTTP 200.</p>
 */
public enum StandardApiErrorCode {
    BAD_REQUEST("BAD_REQUEST", "Bad request."),
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed."),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication is required or invalid."),
    FORBIDDEN("FORBIDDEN", "Access is forbidden."),
    NOT_FOUND("NOT_FOUND", "Resource not found."),
    CONFLICT("CONFLICT", "Resource conflict."),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "HTTP method is not allowed for this endpoint."),
    INVALID_STATE("INVALID_STATE", "Invalid state."),
    TIMEOUT("TIMEOUT", "Operation timed out."),
    RATE_LIMITED("RATE_LIMITED", "Rate limit exceeded."),
    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "Dependency is unavailable."),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal error."),

    CORE_AGENT_NOT_FOUND("CORE_AGENT_NOT_FOUND", "Agent profile not found."),
    CORE_AGENT_GOVERNANCE_REJECTED("CORE_AGENT_GOVERNANCE_REJECTED", "Agent governance rejected the operation."),
    CORE_AGENT_SKILL_NOT_APPROVED("CORE_AGENT_SKILL_NOT_APPROVED", "Agent skill is not approved."),
    CORE_TASK_NOT_FOUND("CORE_TASK_NOT_FOUND", "Task not found."),
    CORE_TASK_INVALID_TRANSITION("CORE_TASK_INVALID_TRANSITION", "Task transition is invalid."),
    CORE_DISPATCH_NO_CANDIDATE("CORE_DISPATCH_NO_CANDIDATE", "No dispatch candidate is available."),
    CORE_DISPATCH_DUPLICATE_REQUEST("CORE_DISPATCH_DUPLICATE_REQUEST", "Dispatch request already exists."),
    CORE_CALLBACK_INVALID_TRANSITION("CORE_CALLBACK_INVALID_TRANSITION", "Callback transition is invalid."),
    CORE_INCIDENT_NOT_FOUND("CORE_INCIDENT_NOT_FOUND", "Incident not found."),
    CORE_OUTBOX_DISPATCH_FAILED("CORE_OUTBOX_DISPATCH_FAILED", "Outbox dispatch failed."),
    CORE_ADAPTER_ACTION_FAILED("CORE_ADAPTER_ACTION_FAILED", "Adapter action failed."),

    TENANT_CONTEXT_REQUIRED("TENANT_CONTEXT_REQUIRED", "Tenant context is required."),
    FLOW_AGENT_PROFILE_NOT_FOUND("FLOW_AGENT_PROFILE_NOT_FOUND", "Selected Agent profile does not exist in this tenant."),
    AGENT_RUNTIME_BINDING_CONFLICT("AGENT_RUNTIME_BINDING_CONFLICT", "An active runtime binding already exists for this Agent.");

    private final String code;
    private final String defaultMessage;

    StandardApiErrorCode(String code, String defaultMessage) {
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
