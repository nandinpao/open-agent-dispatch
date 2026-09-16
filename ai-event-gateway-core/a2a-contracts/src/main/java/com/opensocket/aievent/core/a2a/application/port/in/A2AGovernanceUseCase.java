package com.opensocket.aievent.core.a2a.application.port.in;

import java.util.List;

import com.opensocket.aievent.core.a2a.A2APolicy;
import com.opensocket.aievent.core.a2a.A2APolicyVisibilityScope;
import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.A2ARequestCommand;
import com.opensocket.aievent.core.a2a.A2ADispatchProgressCommand;
import com.opensocket.aievent.core.a2a.A2AStateHistoryEntry;

/**
 * Phase 0 inbound boundary for legacy A2A archive/reconciliation operations.
 *
 * <p>Mutation methods retained in this compatibility interface fail closed and are marked for
 * removal. New capability delegation will use a separate Phase 1 contract rather than reviving
 * Source -> Target routing.</p>
 */
public interface A2AGovernanceUseCase {
    List<A2APolicy> searchPolicies(String tenantId, String sourceDomainId, String targetDomainId, int limit);
    List<A2APolicy> searchPolicies(String tenantId, String sourceDomainId, String targetDomainId, int limit, A2APolicyVisibilityScope scope);
    A2APolicy getPolicy(String tenantId, String policyId);
    @Deprecated(forRemoval = true)
    A2APolicy upsertPolicy(String tenantId, String policyId, A2APolicy value, Long expectedVersion);
    @Deprecated(forRemoval = true)
    A2ARequest request(A2ARequestCommand command);
    @Deprecated(forRemoval = true)
    A2ARequest approve(String tenantId, String requestId, String actorType, String actorId, String idempotencyKey);
    A2ARequest reject(String tenantId, String requestId, String reasonCode, String reason,
                      String actorType, String actorId, String idempotencyKey);
    A2ARequest get(String tenantId, String requestId);
    A2ARequest recordDispatchProgress(A2ADispatchProgressCommand command);
    List<A2ARequest> history(String tenantId, String taskId, int limit);
    List<A2AStateHistoryEntry> stateHistory(String tenantId, String requestId, int limit);
}
