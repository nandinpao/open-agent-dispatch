package com.opensocket.aievent.core.integration.handoff;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Handoff authority persistence. Issue projection persistence is intentionally separate. */
public interface HandoffContextRepository extends HandoffSnapshotReadPort {
    HandoffContextPolicy savePolicy(HandoffContextPolicy value);
    Optional<HandoffContextPolicy> findPolicy(String tenantId, String policyId);
    List<HandoffContextPolicy> listPolicies(String tenantId, int limit);

    HandoffContextSnapshot saveSnapshot(HandoffContextSnapshot value);
    List<HandoffContextSnapshot> findReleaseCandidates(OffsetDateTime now, int limit);

    HandoffReleaseEvidence saveReleaseEvidence(HandoffReleaseEvidence value);
    List<HandoffReleaseEvidence> listReleaseEvidence(String tenantId, String snapshotId, int limit);

    HandoffContextApproval saveApproval(HandoffContextApproval value);
    List<HandoffContextApproval> listApprovals(String tenantId, String snapshotId, int limit);

    ResultContextSnapshot saveResultSnapshot(ResultContextSnapshot value);

    AgentContextAccessEvent saveAccessEvent(AgentContextAccessEvent value);
    List<AgentContextAccessEvent> listAccessEvents(String tenantId, String taskId, int limit);

    String mode();
}
