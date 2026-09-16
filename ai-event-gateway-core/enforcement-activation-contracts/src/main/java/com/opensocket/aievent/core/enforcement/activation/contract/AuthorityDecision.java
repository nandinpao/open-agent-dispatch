package com.opensocket.aievent.core.enforcement.activation.contract;

public record AuthorityDecision(
        AuthorityPlane plane,
        AuthorityMode mode,
        long revision,
        AuthorityRouteKey matchedRoute,
        String reasonCode,
        boolean shadowTargetEvaluation,
        boolean hardGuardDenied,
        int cohortBucketBasisPoints) {

    public AuthorityDecision {
        if (plane == null || mode == null || matchedRoute == null) throw new IllegalArgumentException("plane, mode and matchedRoute are required");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        reasonCode = reasonCode == null || reasonCode.isBlank() ? "AUTHORITY_DECISION" : reasonCode.trim();
        if (cohortBucketBasisPoints < -1 || cohortBucketBasisPoints > 9_999) throw new IllegalArgumentException("cohort bucket is invalid");
        if (hardGuardDenied && plane != AuthorityPlane.NONE) throw new IllegalArgumentException("hard guard denial must use NONE authority plane");
    }

    public static AuthorityDecision hardGuardDenied(AuthorityRoutingContext context, long revision, String reasonCode) {
        return new AuthorityDecision(AuthorityPlane.NONE, AuthorityMode.PAUSED, revision, context.routeKey(), reasonCode, false, true, -1);
    }
}
