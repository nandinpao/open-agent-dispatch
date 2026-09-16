package com.opensocket.aievent.core.issuetracking.contract;

import java.util.Objects;

/** Provider-normalized evidence returned to Issue Tracking Core. */
public record IssueProjectionOutcome(
        IssueProjectionOutcomeStatus status,
        String externalIssueId,
        String externalIssueKey,
        String externalUrl,
        String providerVersion,
        String providerEvidenceReference,
        String failureClassification,
        String recoveryStrategy) {

    public IssueProjectionOutcome {
        status = Objects.requireNonNull(status, "status is required");
        externalIssueId = normalize(externalIssueId);
        externalIssueKey = normalize(externalIssueKey);
        externalUrl = normalize(externalUrl);
        providerVersion = normalize(providerVersion);
        providerEvidenceReference = normalize(providerEvidenceReference);
        failureClassification = normalize(failureClassification);
        recoveryStrategy = normalize(recoveryStrategy);
        if (status == IssueProjectionOutcomeStatus.SYNCED && externalIssueId.isEmpty() && externalIssueKey.isEmpty()) {
            throw new IllegalArgumentException("A synchronized outcome requires an external Issue identifier");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
