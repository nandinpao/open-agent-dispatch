package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowActionExecutionRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowHistoryRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowStore;

class RemediationWorkflowRepositoryDbHardeningContainerTest extends P25RepositoryDbContainerSupport {

    @Test
    void workflowStatusTransitionAndExecutionLeaseMustBeDbGuarded() throws Exception {
        AgentRemediationWorkflowStore store = remediationWorkflowStore();
        store.insertWorkflow(workflow("workflow-db-1", "agent-db-1", "PENDING_APPROVAL"));

        assertThat(store.updateWorkflowStatusIfCurrent(
                "workflow-db-1", "PENDING_APPROVAL", "APPROVED", "approver-a", now()))
                .isEqualTo(1);
        assertThat(store.updateWorkflowStatusIfCurrent(
                "workflow-db-1", "PENDING_APPROVAL", "REJECTED", "approver-b", now()))
                .isZero();

        var leaseTime = now().plusSeconds(1);
        List<Integer> leaseClaims = runConcurrent(List.of(
                () -> store.acquireWorkflowExecutionLease(
                        "workflow-db-1", "APPROVED", "worker-a", "operator-a", leaseTime, leaseTime.plusSeconds(5)),
                () -> store.acquireWorkflowExecutionLease(
                        "workflow-db-1", "APPROVED", "worker-b", "operator-b", leaseTime, leaseTime.plusSeconds(5))));
        assertThat(leaseClaims).containsExactlyInAnyOrder(1, 0);

        AgentRemediationWorkflowRecord leased = store.findWorkflowById("workflow-db-1");
        assertThat(leased.getExecutionLeaseOwner()).isIn("worker-a", "worker-b");
        assertThat(leased.getExecutionLeaseVersion()).isEqualTo(1L);

        assertThat(store.findExpiredWorkflowExecutionLeases(leased.getExecutionLeaseExpiresAt().minusSeconds(1), 10)).isEmpty();
        assertThat(store.findExpiredWorkflowExecutionLeases(leased.getExecutionLeaseExpiresAt().plusSeconds(1), 10))
                .extracting(AgentRemediationWorkflowRecord::getWorkflowId)
                .contains("workflow-db-1");

        assertThat(store.clearExpiredWorkflowExecutionLeaseForOwner(
                "workflow-db-1", "not-the-owner", leased.getExecutionLeaseExpiresAt().plusSeconds(1)))
                .isZero();
        assertThat(store.clearExpiredWorkflowExecutionLeaseForOwner(
                "workflow-db-1", leased.getExecutionLeaseOwner(), leased.getExecutionLeaseExpiresAt().plusSeconds(1)))
                .isEqualTo(1);
        assertThat(store.findWorkflowById("workflow-db-1").getExecutionLeaseOwner()).isNull();
    }

    @Test
    void actionExecutionMustBeIdempotentAndOnlyRunningRowsCanComplete() {
        AgentRemediationWorkflowStore store = remediationWorkflowStore();
        store.insertWorkflow(workflow("workflow-db-2", "agent-db-2", "APPROVED"));
        store.insertHistory(history("history-db-1", "workflow-db-2", "agent-db-2", "APPROVED"));

        assertThat(store.findHistoryByEventType("APPROVED", 10))
                .extracting(AgentRemediationWorkflowHistoryRecord::getHistoryId)
                .contains("history-db-1");

        assertThat(store.insertActionExecutionIfAbsent(action("action-exec-db-1", "workflow-db-2", "action-1")))
                .isEqualTo(1);
        assertThat(store.insertActionExecutionIfAbsent(action("action-exec-db-duplicate", "workflow-db-2", "action-1")))
                .isZero();
        assertThat(store.findActionExecutionsByWorkflowId("workflow-db-2"))
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.getActionExecutionId()).isEqualTo("action-exec-db-1");
                    assertThat(action.getStatus()).isEqualTo("PENDING");
                });

        assertThat(store.completeActionExecutionIfRunning(
                "action-exec-db-1", "SUCCEEDED", "{\"ok\":true}", null, now()))
                .isZero();
        assertThat(store.claimActionExecutionForRun("action-exec-db-1", "operator-a", "execute", now()))
                .isEqualTo(1);
        assertThat(store.claimActionExecutionForRun("action-exec-db-1", "operator-b", "duplicate", now()))
                .isZero();
        assertThat(store.completeActionExecutionIfRunning(
                "action-exec-db-1", "SUCCEEDED", "{\"ok\":true}", null, now().plusSeconds(5)))
                .isEqualTo(1);
        assertThat(store.findActionExecutionById("action-exec-db-1"))
                .satisfies(action -> {
                    assertThat(action.getStatus()).isEqualTo("SUCCEEDED");
                    assertThat(action.getAttemptCount()).isEqualTo(1);
                    assertThat(action.getCompletedAt()).isNotNull();
                    assertThat(action.getLastResultJson()).contains("ok");
                });
    }

    private AgentRemediationWorkflowRecord workflow(String workflowId, String agentId, String status) {
        AgentRemediationWorkflowRecord record = new AgentRemediationWorkflowRecord();
        record.setWorkflowId(workflowId);
        record.setProposalId("proposal-" + workflowId);
        record.setAgentId(agentId);
        record.setStatus(status);
        record.setSeverity("HIGH");
        record.setApprovalRequired(true);
        record.setCreatedBy("qa");
        record.setLastOperatorId("qa");
        record.setRollbackSuggestionsJson("[]");
        record.setActionsJson("[{\"actionId\":\"action-1\",\"type\":\"ROTATE_CREDENTIAL\"}]");
        record.setMetadataJson("{\"stage\":\"P25\"}");
        record.setVersion(0L);
        record.setCreatedAt(now());
        record.setUpdatedAt(now());
        return record;
    }

    private AgentRemediationWorkflowHistoryRecord history(String historyId, String workflowId, String agentId, String eventType) {
        AgentRemediationWorkflowHistoryRecord record = new AgentRemediationWorkflowHistoryRecord();
        record.setHistoryId(historyId);
        record.setWorkflowId(workflowId);
        record.setAgentId(agentId);
        record.setEventType(eventType);
        record.setOperatorId("qa");
        record.setReason("repository DB hardening");
        record.setMetadataJson("{\"stage\":\"P25\"}");
        record.setOccurredAt(now());
        return record;
    }

    private AgentRemediationWorkflowActionExecutionRecord action(String actionExecutionId, String workflowId, String actionId) {
        AgentRemediationWorkflowActionExecutionRecord record = new AgentRemediationWorkflowActionExecutionRecord();
        record.setActionExecutionId(actionExecutionId);
        record.setWorkflowId(workflowId);
        record.setAgentId("agent-db-2");
        record.setActionId(actionId);
        record.setActionType("ROTATE_CREDENTIAL");
        record.setIdempotencyKey("idem-" + workflowId + "-" + actionId);
        record.setStatus("PENDING");
        record.setAttemptCount(0);
        record.setLastResultJson("{}");
        record.setCreatedAt(now());
        record.setUpdatedAt(now());
        return record;
    }
}
