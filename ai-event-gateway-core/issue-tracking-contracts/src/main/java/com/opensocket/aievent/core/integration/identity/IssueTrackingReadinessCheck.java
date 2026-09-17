package com.opensocket.aievent.core.integration.identity;

/** One user-facing, server-authoritative Issue Tracking readiness dimension. */
public record IssueTrackingReadinessCheck(
        String code,
        String label,
        String status,
        String reasonCode,
        String summary,
        String remediationRoute) {}
