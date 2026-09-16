package com.opensocket.aievent.core.issuetracking.connector;

public record IssueConnectorResult(
        IssueConnectorOperation operation,
        boolean success,
        String provider,
        String issueId,
        String issueUrl,
        String issueStatus,
        String responseRef,
        Integer providerStatusCode,
        boolean retryable,
        IssueProviderFailureCode failureCode,
        IssueProviderHealthImpact healthImpact,
        IssueProviderOutcomeCertainty outcomeCertainty,
        String errorMessage) {

    public IssueConnectorResult {
        outcomeCertainty = outcomeCertainty == null ? IssueProviderOutcomeCertainty.CONFIRMED : outcomeCertainty;
    }

    public static IssueConnectorResult success(
            IssueConnectorOperation operation,
            String provider,
            String issueId,
            String issueUrl,
            String issueStatus,
            String responseRef,
            Integer providerStatusCode) {
        return new IssueConnectorResult(operation, true, provider, issueId, issueUrl, issueStatus,
                responseRef, providerStatusCode, false, null, IssueProviderHealthImpact.HEALTHY,
                IssueProviderOutcomeCertainty.CONFIRMED, null);
    }

    /** Compatibility factory for non-HTTP/configuration failures. */
    public static IssueConnectorResult failure(
            IssueConnectorOperation operation,
            String provider,
            Integer providerStatusCode,
            boolean retryable,
            String errorMessage) {
        return failure(operation, provider, providerStatusCode, retryable,
                IssueProviderFailureCode.ISSUE_PROVIDER_EXECUTION_FAILED,
                providerStatusCode == null ? IssueProviderHealthImpact.NONE : IssueProviderHealthImpact.DEGRADED,
                errorMessage);
    }

    public static IssueConnectorResult failure(
            IssueConnectorOperation operation,
            String provider,
            Integer providerStatusCode,
            boolean retryable,
            IssueProviderFailureCode failureCode,
            IssueProviderHealthImpact healthImpact,
            String errorMessage) {
        return new IssueConnectorResult(operation, false, provider, null, null, null,
                null, providerStatusCode, retryable, failureCode,
                healthImpact == null ? IssueProviderHealthImpact.NONE : healthImpact,
                IssueProviderOutcomeCertainty.CONFIRMED, errorMessage);
    }

    public static IssueConnectorResult uncertain(
            IssueConnectorOperation operation,
            String provider,
            Integer providerStatusCode,
            IssueProviderHealthImpact healthImpact,
            String errorMessage) {
        return new IssueConnectorResult(operation, false, provider, null, null, null,
                null, providerStatusCode, false, IssueProviderFailureCode.ISSUE_PROVIDER_OUTCOME_UNCERTAIN,
                healthImpact == null ? IssueProviderHealthImpact.DEGRADED : healthImpact,
                IssueProviderOutcomeCertainty.UNCERTAIN, errorMessage);
    }
}
