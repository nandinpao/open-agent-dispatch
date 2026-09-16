package com.opensocket.aievent.core.a2a.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.a2a.*;

class A2APolicySnapshotFactoryTest {
    private final A2APolicySnapshotFactory factory = new A2APolicySnapshotFactory();

    @Test
    void snapshotHashIsDeterministicAcrossCapabilityOrderAndDuplicates() {
        A2APolicy policy = policy(9);
        var first = factory.create(policy, "ERP", "MES", "INCIDENT", "triage",
                List.of("VISION", "SQL", "VISION"));
        var second = factory.create(policy, "ERP", "MES", "INCIDENT", "triage",
                List.of("SQL", "VISION"));
        assertEquals(first.snapshotHash(), second.snapshotHash());
        assertEquals(List.of("SQL", "VISION"), first.agentCapabilityCodes());
        assertTrue(factory.verify(first));
    }

    @Test
    void policyVersionAndAggregationAreBoundToAcceptedRequest() {
        A2APolicy policy = policy(7);
        var snapshot = factory.create(policy, "ERP", "MES", "INCIDENT", null, List.of());
        policy.setVersion(8);
        policy.setResultAggregationPolicy(A2AResultAggregationPolicy.ANY_SUCCESS);
        assertEquals(7, snapshot.policyVersion());
        assertEquals(A2AResultAggregationPolicy.ALL_SUCCESS, snapshot.aggregationPolicy());
        assertTrue(factory.verify(snapshot));
    }

    @Test
    void changedSnapshotValuesFailIntegrityVerification() {
        A2ADirectionalPolicySnapshot snapshot = factory.create(
                policy(3), "ERP", "MES", "INCIDENT", null, List.of("SQL"));
        var tampered = new A2ADirectionalPolicySnapshot(
                snapshot.policyId(), snapshot.policyVersion(), snapshot.sourceDomainId(),
                snapshot.targetDomainId(), snapshot.taskType(), snapshot.serviceCode(),
                snapshot.agentCapabilityCodes(), "different-pool", snapshot.approvalMode(),
                snapshot.hopLimit(), snapshot.timeoutSeconds(), snapshot.aggregationPolicy(),
                snapshot.aggregationQuorum(), snapshot.cancellationPolicy(), snapshot.failurePropagationPolicy(),
                snapshot.handoffPolicyId(), snapshot.handoffRequirement(),
                snapshot.issueProjectionPolicy(), snapshot.snapshotHash());
        assertFalse(factory.verify(tampered));
    }

    private A2APolicy policy(long version) {
        A2APolicy policy = new A2APolicy();
        policy.setPolicyId("policy-erp-mes");
        policy.setVersion(version);
        policy.setTargetAgentPoolId("mes-pool");
        policy.setApprovalMode(A2AApprovalMode.OPERATOR);
        policy.setMaxHopCount(4);
        policy.setTimeoutSeconds(300);
        policy.setResultAggregationPolicy(A2AResultAggregationPolicy.ALL_SUCCESS);
        policy.setCancellationPolicy(A2ACancellationPolicy.CANCEL_ALL_CHILDREN);
        policy.setFailurePropagationPolicy(A2AFailurePropagationPolicy.FAIL_PARENT);
        policy.setHandoffContextPolicyId("handoff-default");
        policy.setHandoffContextRequirement("REQUIRED_BEFORE_DISPATCH");
        policy.setIssueProjectionPolicy("CREATE_ON_CHILD");
        return policy;
    }
}
