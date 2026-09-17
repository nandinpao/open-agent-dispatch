package com.opensocket.aievent.core.integration.identity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Server-authoritative readiness projection for Source System Issue Tracking.
 *
 * <p>Admin UI surfaces must display this contract instead of inferring readiness by combining
 * mapping, connection and credential metadata in the browser.</p>
 */
public record IssueTrackingRuntimeReadiness(
        String sourceSystemId,
        String taskType,
        String overallStatus,
        boolean configured,
        boolean runtimeReady,
        boolean providerAuthenticated,
        boolean liveCreateCertified,
        String liveCreateCertificationStatus,
        String executionAuthority,
        boolean autoExecutePending,
        boolean connectorRuntimeEnabled,
        String connectionId,
        String mappingId,
        String externalProjectId,
        String externalProjectKey,
        String externalTrackerId,
        String technicalPrincipalId,
        String credentialId,
        List<String> blockers,
        List<IssueTrackingReadinessCheck> checks,
        OffsetDateTime evaluatedAt) {
    public IssueTrackingRuntimeReadiness {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
