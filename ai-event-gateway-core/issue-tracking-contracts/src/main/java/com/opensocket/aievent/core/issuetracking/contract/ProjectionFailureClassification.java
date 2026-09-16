package com.opensocket.aievent.core.issuetracking.contract;

/** Why a Projection failed. Kept separate from lifecycle and recovery strategy. */
public enum ProjectionFailureClassification {
    NONE,
    MAPPING_NOT_FOUND,
    MAPPING_NOT_ACTIVE,
    MAPPING_SCHEMA_DRIFT,
    PRINCIPAL_SCOPE_DENIED,
    PRINCIPAL_PERMISSION_DENIED,
    CANONICAL_DOCUMENT_INVALID,
    SENSITIVE_FIELD_BLOCKED,
    IDEMPOTENCY_CONFLICT,
    STALE_DOMAIN_EVENT,
    VERSION_CONFLICT,
    PROVIDER_REJECTED,
    PROVIDER_TRANSIENT_FAILURE,
    EXTERNAL_STATE_CONFLICT,
    RETRY_EXHAUSTED,
    DISABLED_BY_OPERATOR
}
