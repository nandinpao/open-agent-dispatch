package com.opensocket.aievent.core.uicapability.contract;

/** General-user-safe reason categories. Raw permission and policy details are excluded. */
public enum UiReasonCategory {
    NOT_ALLOWED,
    OUTSIDE_SCOPE,
    READ_ONLY_ACCESS,
    APPROVAL_REQUIRED,
    STEP_UP_REQUIRED,
    SEPARATION_OF_DUTIES,
    RESOURCE_LOCKED,
    RESOURCE_QUARANTINED,
    RESOURCE_CHANGED,
    TENANT_CONTEXT_CHANGED,
    ACTION_NOT_AVAILABLE_IN_CURRENT_STATE,
    BACKGROUND_OPERATION_IN_PROGRESS,
    EXTERNAL_PROVIDER_UNAVAILABLE,
    TEMPORARILY_UNAVAILABLE
}
