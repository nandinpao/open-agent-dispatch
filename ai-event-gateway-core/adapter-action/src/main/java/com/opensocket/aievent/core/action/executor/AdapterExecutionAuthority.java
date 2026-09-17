package com.opensocket.aievent.core.action.executor;

/**
 * Canonical runtime owner for an AdapterAction side effect.
 *
 * ISSUE_TRACKING intentionally never uses EXTERNAL_WORKER. Provider credentials,
 * project mappings and technical identities remain server-side Core authority.
 */
public enum AdapterExecutionAuthority {
    CORE_GOVERNED,
    EXTERNAL_WORKER,
    DISABLED
}
