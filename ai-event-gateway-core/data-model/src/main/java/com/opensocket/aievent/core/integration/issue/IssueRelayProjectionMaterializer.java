package com.opensocket.aievent.core.integration.issue;

import com.opensocket.aievent.core.integration.issue.projection.CrossProjectIssueRelay;
import com.opensocket.aievent.core.integration.identity.IntegrationConnection;
import com.opensocket.aievent.core.integration.identity.IntegrationProjectMapping;

/**
 * Materializes a completed Phase 0F relay into the canonical Phase 0G Task-Issue graph.
 * Provider mutations have already happened before this callback; implementations must not
 * create another external issue or duplicate a provider relation.
 */
public interface IssueRelayProjectionMaterializer {
    void materializeRelay(CrossProjectIssueRelay relay,
                     IntegrationProjectMapping targetMapping,
                     IntegrationConnection targetConnection,
                     String targetIssueId,
                     String targetIssueUrl);

    static IssueRelayProjectionMaterializer noop() {
        return (relay, mapping, connection, issueId, issueUrl) -> { };
    }
}
