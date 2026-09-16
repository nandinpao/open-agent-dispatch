package com.opensocket.aievent.core.integration.issue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.integration.handoff.HandoffContextSnapshot;
import com.opensocket.aievent.core.integration.issue.projection.CrossProjectIssueRelay;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayProviderGateway;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayProviderResult;

/** Safe provider fallback owned by Issue Projection, not Handoff Core. */
@Component
@ConditionalOnProperty(prefix = "issue-projection", name = "relay-provider", havingValue = "METADATA_ONLY")
public class MetadataOnlyIssueRelayProviderGateway implements IssueRelayProviderGateway {
    public IssueRelayProviderResult createTargetIssue(
            CrossProjectIssueRelay relay, HandoffContextSnapshot snapshot) {
        return IssueRelayProviderResult.failure(true, null, "ISSUE_RELAY_PROVIDER_NOT_CONFIGURED",
                "No scoped Issue Relay provider gateway is installed.");
    }

    public IssueRelayProviderResult createNativeRelation(
            CrossProjectIssueRelay relay, String sourceIssueId, String targetIssueId) {
        return IssueRelayProviderResult.failure(false, null,
                "ISSUE_RELAY_NATIVE_RELATION_UNAVAILABLE",
                "Native relation support was not probed.");
    }

    public IssueRelayProviderResult appendSourceBacklink(
            CrossProjectIssueRelay relay, String sourceIssueId,
            String targetIssueId, String targetIssueUrl) {
        return IssueRelayProviderResult.failure(true, null, "ISSUE_RELAY_PROVIDER_NOT_CONFIGURED",
                "Source backlink provider is unavailable.");
    }

    public IssueRelayProviderResult appendTargetBacklink(
            CrossProjectIssueRelay relay, String sourceIssueId,
            String sourceIssueUrl, String targetIssueId) {
        return IssueRelayProviderResult.failure(true, null, "ISSUE_RELAY_PROVIDER_NOT_CONFIGURED",
                "Target backlink provider is unavailable.");
    }

    public String mode() {
        return "METADATA_ONLY";
    }
}
