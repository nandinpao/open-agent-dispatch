package com.opensocket.aievent.core.integration.issue.policy;

import java.util.List;
import java.util.Optional;

public interface IssuePolicyDecisionRepository {
    IssuePolicyDecision save(IssuePolicyDecision value);
    Optional<IssuePolicyDecision> find(String tenantId, String decisionId);
    Optional<IssuePolicyDecision> findByTaskAndPurpose(String tenantId, String taskId, String projectionPurpose);
    List<IssuePolicyDecision> listByTask(String tenantId, String taskId, int limit);
}
