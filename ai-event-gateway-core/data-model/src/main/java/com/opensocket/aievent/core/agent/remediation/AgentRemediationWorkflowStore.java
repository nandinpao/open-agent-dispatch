package com.opensocket.aievent.core.agent.remediation;

import java.time.OffsetDateTime;
import java.util.List;

public interface AgentRemediationWorkflowStore {
    int insertWorkflow(AgentRemediationWorkflowRecord record);

    AgentRemediationWorkflowRecord findWorkflowById(String workflowId);

    List<AgentRemediationWorkflowRecord> findWorkflowsByAgentId(String agentId, int limit);

    int updateWorkflowStatusIfCurrent(String workflowId,
                                      String expectedStatus,
                                      String nextStatus,
                                      String lastOperatorId,
                                      OffsetDateTime updatedAt);

    int acquireWorkflowExecutionLease(String workflowId,
                                      String expectedStatus,
                                      String leaseOwner,
                                      String lastOperatorId,
                                      OffsetDateTime acquiredAt,
                                      OffsetDateTime expiresAt);

    int releaseWorkflowExecutionLease(String workflowId, String leaseOwner, OffsetDateTime updatedAt);

    int clearExpiredWorkflowExecutionLease(String workflowId, OffsetDateTime now);

    int clearExpiredWorkflowExecutionLeaseForOwner(String workflowId, String leaseOwner, OffsetDateTime now);

    List<AgentRemediationWorkflowRecord> findExpiredWorkflowExecutionLeases(OffsetDateTime now, int limit);

    int insertHistory(AgentRemediationWorkflowHistoryRecord record);

    List<AgentRemediationWorkflowHistoryRecord> findHistoryByWorkflowId(String workflowId);

    List<AgentRemediationWorkflowHistoryRecord> findHistoryByEventType(String eventType, int limit);

    int insertActionExecutionIfAbsent(AgentRemediationWorkflowActionExecutionRecord record);

    List<AgentRemediationWorkflowActionExecutionRecord> findActionExecutionsByWorkflowId(String workflowId);

    AgentRemediationWorkflowActionExecutionRecord findActionExecutionById(String actionExecutionId);

    int claimActionExecutionForRun(String actionExecutionId,
                                   String lastOperatorId,
                                   String lastReason,
                                   OffsetDateTime updatedAt);

    int completeActionExecutionIfRunning(String actionExecutionId,
                                         String nextStatus,
                                         String lastResultJson,
                                         String lastError,
                                         OffsetDateTime updatedAt);
}
