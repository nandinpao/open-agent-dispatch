package com.opensocket.aievent.core.uicapability.contract;

/** Canonical Phase 7 API route names. Implementations are introduced after Phase 7A-0. */
public final class UiCapabilityApiPaths {
    public static final String PAGE_BOOTSTRAP = "/api/ui/bootstrap/{pageContext}";
    public static final String CAPABILITY_BATCH = "/api/ui/capabilities:batch";
    public static final String LIST_CAPABILITY_BATCH = "/api/ui/list-capabilities:batch";
    public static final String CURRENT_SECURITY_EPOCH = "/api/ui/security-epochs/current";
    public static final String SESSION_RISK_STATE = "/api/ui/session-risk-state";

    private UiCapabilityApiPaths() {
    }
}
