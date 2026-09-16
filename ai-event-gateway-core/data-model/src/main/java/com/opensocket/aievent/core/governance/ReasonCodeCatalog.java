package com.opensocket.aievent.core.governance;

import java.util.Set;

public final class ReasonCodeCatalog {
    private ReasonCodeCatalog() { }

    public static final Set<String> ALL = Set.of(
            "TENANT_CONTEXT_REQUIRED",
            "API_IDEMPOTENCY_KEY_REQUIRED",
            "API_CORRELATION_ID_REQUIRED",
            "API_EXPECTED_VERSION_REQUIRED",
            "API_EXPECTED_VERSION_INVALID",
            "API_ACTOR_IDENTITY_REQUIRED",
            "API_AUDIT_REASON_REQUIRED",
            "API_IDEMPOTENCY_REPLAY",
            "API_IDEMPOTENCY_IN_PROGRESS",
            "API_IDEMPOTENCY_CONFLICT",
            "RESOURCE_VERSION_CONFLICT",
            "AUTHORIZATION_DENIED",
            "PERMISSION_POINT_UNKNOWN",
            "HANDOFF_CONTEXT_REQUIRED_BEFORE_COMPLETION",
            "AUDIT_EVIDENCE_WRITE_FAILED",
            "EVENT_ENVELOPE_REQUIRED_FIELD_MISSING",
            "EVENT_SCHEMA_VERSION_UNSUPPORTED",
            "CROSS_TENANT_MUTATION_DENIED",
            "UNAUTHORIZED_MUTATION",
            "EXTERNAL_ISSUE_CLOSED_TASK_ACTIVE",
            "WEBHOOK_REPLAY_CONFLICT",
            "INTEGRATION_SYNC_DEAD_LETTERED");

    public static boolean known(String value) {
        return value != null && ALL.contains(value);
    }
}
