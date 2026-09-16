package com.opensocket.aievent.core.integration.issue.projection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface IssueRelayRepository {
    CrossProjectIssueRelay saveRelay(CrossProjectIssueRelay value);
    Optional<CrossProjectIssueRelay> findRelay(String tenantId, String relayId);
    Optional<CrossProjectIssueRelay> findRelayByIdempotencyKey(String tenantId, String key);
    List<CrossProjectIssueRelay> listRelays(String tenantId, String taskId, int limit);
    List<CrossProjectIssueRelay> listDueRelays(OffsetDateTime now, int limit);
    IssueRelayAttempt saveRelayAttempt(IssueRelayAttempt value);
    List<IssueRelayAttempt> listRelayAttempts(String tenantId, String relayId, int limit);
    String mode();
}
