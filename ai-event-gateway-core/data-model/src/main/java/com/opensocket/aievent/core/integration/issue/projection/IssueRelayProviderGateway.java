package com.opensocket.aievent.core.integration.issue.projection;

import com.opensocket.aievent.core.integration.handoff.HandoffContextSnapshot;

public interface IssueRelayProviderGateway {
 IssueRelayProviderResult createTargetIssue(CrossProjectIssueRelay relay,HandoffContextSnapshot snapshot);
 IssueRelayProviderResult createNativeRelation(CrossProjectIssueRelay relay,String sourceIssueId,String targetIssueId);
 IssueRelayProviderResult appendSourceBacklink(CrossProjectIssueRelay relay,String sourceIssueId,String targetIssueId,String targetIssueUrl);
 IssueRelayProviderResult appendTargetBacklink(CrossProjectIssueRelay relay,String sourceIssueId,String sourceIssueUrl,String targetIssueId);
 String mode();
}
