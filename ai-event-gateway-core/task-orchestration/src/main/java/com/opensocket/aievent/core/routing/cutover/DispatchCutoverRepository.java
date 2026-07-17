package com.opensocket.aievent.core.routing.cutover;

import java.util.List;
import java.util.Optional;

public interface DispatchCutoverRepository {
    Optional<DispatchCutoverPolicy> findEffectivePolicy(String tenantId, String flowId);
    Optional<DispatchCutoverPolicy> findPolicy(String tenantId, String policyId);
    List<DispatchCutoverPolicy> listPolicies(String tenantId, int limit);
    DispatchCutoverPolicy savePolicy(DispatchCutoverPolicy policy);
    DispatchCutoverDecision appendDecision(DispatchCutoverDecision decision);
    DispatchCutoverOutcome appendOutcome(DispatchCutoverOutcome outcome);
    DispatchCutoverReadiness readiness(String tenantId, String flowId);
    boolean markRolledBack(String tenantId, String policyId, String reason, String actor);
}
