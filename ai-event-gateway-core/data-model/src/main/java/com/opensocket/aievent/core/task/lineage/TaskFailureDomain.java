package com.opensocket.aievent.core.task.lineage;

/**
 * Phase 12.4 coarse failure attribution for investigation/analytics.
 * It explains where a failure manifested; it is not a blame or authorization decision.
 */
public enum TaskFailureDomain {
    NONE,
    CALLER_INPUT,
    AUTHENTICATION,
    AUTHORIZATION,
    ROUTING,
    AGENT_RUNTIME,
    A2A,
    EXTERNAL_SYSTEM,
    PLATFORM,
    UNKNOWN
}
