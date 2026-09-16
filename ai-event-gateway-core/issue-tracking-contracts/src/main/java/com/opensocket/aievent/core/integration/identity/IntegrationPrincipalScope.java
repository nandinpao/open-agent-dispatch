package com.opensocket.aievent.core.integration.identity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** Least-privilege Project, operation and issue-type scope for one Integration Principal. */
public record IntegrationPrincipalScope(
        String tenantId,
        String principalId,
        IntegrationIsolationMode isolationMode,
        String scopeReference,
        List<String> allowedProjectIds,
        List<IntegrationOperation> allowedOperations,
        List<String> allowedIssueTypes,
        boolean productionAllowed,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public IntegrationPrincipalScope {
        allowedProjectIds = allowedProjectIds == null ? List.of() : List.copyOf(allowedProjectIds);
        allowedOperations = allowedOperations == null ? List.of() : List.copyOf(allowedOperations);
        allowedIssueTypes = allowedIssueTypes == null ? List.of() : List.copyOf(allowedIssueTypes);
    }

    public boolean allowsProject(String projectId, String trustZoneId) {
        if (isolationMode == IntegrationIsolationMode.GLOBAL_ADMIN) return false;
        if (isolationMode == IntegrationIsolationMode.PER_PROJECT) return Objects.equals(scopeReference, projectId);
        if (isolationMode == IntegrationIsolationMode.PER_TRUST_ZONE) {
            return scopeReference != null && scopeReference.equals(trustZoneId) && allowedProjectIds.contains(projectId);
        }
        return allowedProjectIds.contains(projectId);
    }

    public boolean allowsOperation(IntegrationOperation operation) {
        return operation != null && allowedOperations.contains(operation);
    }

    public boolean allowsIssueType(String issueType) {
        return issueType != null && !issueType.isBlank()
                && (allowedIssueTypes.contains("*") || allowedIssueTypes.contains(issueType));
    }
}
