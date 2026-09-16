package com.opensocket.aievent.core.a2a;

import java.util.List;

/** Immutable policy binding copied onto each accepted A2A Request. */
public record A2ADirectionalPolicySnapshot(
        String policyId,
        long policyVersion,
        String sourceDomainId,
        String targetDomainId,
        String taskType,
        String serviceCode,
        List<String> agentCapabilityCodes,
        String targetAgentPoolId,
        A2AApprovalMode approvalMode,
        int hopLimit,
        int timeoutSeconds,
        A2AResultAggregationPolicy aggregationPolicy,
        int aggregationQuorum,
        A2ACancellationPolicy cancellationPolicy,
        A2AFailurePropagationPolicy failurePropagationPolicy,
        String handoffPolicyId,
        String handoffRequirement,
        String issueProjectionPolicy,
        String snapshotHash) {

    public A2ADirectionalPolicySnapshot {
        agentCapabilityCodes = agentCapabilityCodes == null ? List.of() : List.copyOf(agentCapabilityCodes);
    }
}
