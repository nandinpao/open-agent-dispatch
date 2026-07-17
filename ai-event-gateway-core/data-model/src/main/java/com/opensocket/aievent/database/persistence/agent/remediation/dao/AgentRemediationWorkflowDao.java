package com.opensocket.aievent.database.persistence.agent.remediation.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.agent.remediation.po.AgentRemediationWorkflowActionExecutionPo;
import com.opensocket.aievent.database.persistence.agent.remediation.po.AgentRemediationWorkflowHistoryPo;
import com.opensocket.aievent.database.persistence.agent.remediation.po.AgentRemediationWorkflowPo;

@Mapper
public interface AgentRemediationWorkflowDao {
    int insertWorkflow(@Param("record") AgentRemediationWorkflowPo record);

    AgentRemediationWorkflowPo findWorkflowById(@Param("workflowId") String workflowId);

    List<AgentRemediationWorkflowPo> findWorkflowsByAgentId(@Param("agentId") String agentId,
                                                            @Param("limit") int limit);

    int updateWorkflowStatusIfCurrent(@Param("workflowId") String workflowId,
                                      @Param("expectedStatus") String expectedStatus,
                                      @Param("nextStatus") String nextStatus,
                                      @Param("lastOperatorId") String lastOperatorId,
                                      @Param("updatedAt") OffsetDateTime updatedAt);

    int acquireWorkflowExecutionLease(@Param("workflowId") String workflowId,
                                      @Param("expectedStatus") String expectedStatus,
                                      @Param("leaseOwner") String leaseOwner,
                                      @Param("lastOperatorId") String lastOperatorId,
                                      @Param("acquiredAt") OffsetDateTime acquiredAt,
                                      @Param("expiresAt") OffsetDateTime expiresAt);

    int releaseWorkflowExecutionLease(@Param("workflowId") String workflowId,
                                      @Param("leaseOwner") String leaseOwner,
                                      @Param("updatedAt") OffsetDateTime updatedAt);

    int clearExpiredWorkflowExecutionLease(@Param("workflowId") String workflowId,
                                           @Param("now") OffsetDateTime now);

    int clearExpiredWorkflowExecutionLeaseForOwner(@Param("workflowId") String workflowId,
                                                   @Param("leaseOwner") String leaseOwner,
                                                   @Param("now") OffsetDateTime now);

    List<AgentRemediationWorkflowPo> findExpiredWorkflowExecutionLeases(@Param("now") OffsetDateTime now,
                                                                        @Param("limit") int limit);

    int insertHistory(@Param("record") AgentRemediationWorkflowHistoryPo record);

    List<AgentRemediationWorkflowHistoryPo> findHistoryByWorkflowId(@Param("workflowId") String workflowId);

    List<AgentRemediationWorkflowHistoryPo> findHistoryByEventType(@Param("eventType") String eventType,
                                                                   @Param("limit") int limit);

    int insertActionExecutionIfAbsent(@Param("record") AgentRemediationWorkflowActionExecutionPo record);

    List<AgentRemediationWorkflowActionExecutionPo> findActionExecutionsByWorkflowId(@Param("workflowId") String workflowId);

    AgentRemediationWorkflowActionExecutionPo findActionExecutionById(@Param("actionExecutionId") String actionExecutionId);

    int claimActionExecutionForRun(@Param("actionExecutionId") String actionExecutionId,
                                   @Param("lastOperatorId") String lastOperatorId,
                                   @Param("lastReason") String lastReason,
                                   @Param("updatedAt") OffsetDateTime updatedAt);

    int completeActionExecutionIfRunning(@Param("actionExecutionId") String actionExecutionId,
                                         @Param("nextStatus") String nextStatus,
                                         @Param("lastResultJson") String lastResultJson,
                                         @Param("lastError") String lastError,
                                         @Param("updatedAt") OffsetDateTime updatedAt);

}
