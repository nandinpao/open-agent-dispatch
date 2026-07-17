package com.opensocket.aievent.database.persistence.agent.remediation.repository;

import java.time.OffsetDateTime;
import java.util.List;

import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowActionExecutionRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowHistoryRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.database.persistence.agent.remediation.dao.AgentRemediationWorkflowDao;
import com.opensocket.aievent.database.persistence.agent.remediation.po.AgentRemediationWorkflowActionExecutionPo;
import com.opensocket.aievent.database.persistence.agent.remediation.po.AgentRemediationWorkflowHistoryPo;
import com.opensocket.aievent.database.persistence.agent.remediation.po.AgentRemediationWorkflowPo;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "agent-remediation.workflow", name = "store", havingValue = "MYBATIS", matchIfMissing = true)
public class MybatisAgentRemediationWorkflowStore implements AgentRemediationWorkflowStore {
    private final AgentRemediationWorkflowDao dao;

    public MybatisAgentRemediationWorkflowStore(AgentRemediationWorkflowDao dao) {
        this.dao = dao;
    }

    @Override
    public int insertWorkflow(AgentRemediationWorkflowRecord record) {
        return dao.insertWorkflow(toPo(record));
    }

    @Override
    public AgentRemediationWorkflowRecord findWorkflowById(String workflowId) {
        return toRecord(dao.findWorkflowById(workflowId));
    }

    @Override
    public List<AgentRemediationWorkflowRecord> findWorkflowsByAgentId(String agentId, int limit) {
        return dao.findWorkflowsByAgentId(agentId, limit).stream().map(this::toRecord).toList();
    }

    @Override
    public int updateWorkflowStatusIfCurrent(String workflowId, String expectedStatus, String nextStatus, String lastOperatorId, OffsetDateTime updatedAt) {
        return dao.updateWorkflowStatusIfCurrent(workflowId, expectedStatus, nextStatus, lastOperatorId, updatedAt);
    }

    @Override
    public int acquireWorkflowExecutionLease(String workflowId, String expectedStatus, String leaseOwner, String lastOperatorId, OffsetDateTime acquiredAt, OffsetDateTime expiresAt) {
        return dao.acquireWorkflowExecutionLease(workflowId, expectedStatus, leaseOwner, lastOperatorId, acquiredAt, expiresAt);
    }

    @Override
    public int releaseWorkflowExecutionLease(String workflowId, String leaseOwner, OffsetDateTime updatedAt) {
        return dao.releaseWorkflowExecutionLease(workflowId, leaseOwner, updatedAt);
    }

    @Override
    public int clearExpiredWorkflowExecutionLease(String workflowId, OffsetDateTime now) {
        return dao.clearExpiredWorkflowExecutionLease(workflowId, now);
    }

    @Override
    public int clearExpiredWorkflowExecutionLeaseForOwner(String workflowId, String leaseOwner, OffsetDateTime now) {
        return dao.clearExpiredWorkflowExecutionLeaseForOwner(workflowId, leaseOwner, now);
    }

    @Override
    public List<AgentRemediationWorkflowRecord> findExpiredWorkflowExecutionLeases(OffsetDateTime now, int limit) {
        return dao.findExpiredWorkflowExecutionLeases(now, limit).stream().map(this::toRecord).toList();
    }

    @Override
    public int insertHistory(AgentRemediationWorkflowHistoryRecord record) {
        return dao.insertHistory(toPo(record));
    }

    @Override
    public List<AgentRemediationWorkflowHistoryRecord> findHistoryByWorkflowId(String workflowId) {
        return dao.findHistoryByWorkflowId(workflowId).stream().map(this::toRecord).toList();
    }

    @Override
    public List<AgentRemediationWorkflowHistoryRecord> findHistoryByEventType(String eventType, int limit) {
        return dao.findHistoryByEventType(eventType, limit).stream().map(this::toRecord).toList();
    }

    @Override
    public int insertActionExecutionIfAbsent(AgentRemediationWorkflowActionExecutionRecord record) {
        return dao.insertActionExecutionIfAbsent(toPo(record));
    }

    @Override
    public List<AgentRemediationWorkflowActionExecutionRecord> findActionExecutionsByWorkflowId(String workflowId) {
        return dao.findActionExecutionsByWorkflowId(workflowId).stream().map(this::toRecord).toList();
    }

    @Override
    public AgentRemediationWorkflowActionExecutionRecord findActionExecutionById(String actionExecutionId) {
        return toRecord(dao.findActionExecutionById(actionExecutionId));
    }

    @Override
    public int claimActionExecutionForRun(String actionExecutionId, String lastOperatorId, String lastReason, OffsetDateTime updatedAt) {
        return dao.claimActionExecutionForRun(actionExecutionId, lastOperatorId, lastReason, updatedAt);
    }

    @Override
    public int completeActionExecutionIfRunning(String actionExecutionId, String nextStatus, String lastResultJson, String lastError, OffsetDateTime updatedAt) {
        return dao.completeActionExecutionIfRunning(actionExecutionId, nextStatus, lastResultJson, lastError, updatedAt);
    }

    private AgentRemediationWorkflowPo toPo(AgentRemediationWorkflowRecord source) {
        if (source == null) return null;
        AgentRemediationWorkflowPo target = new AgentRemediationWorkflowPo();
        target.setWorkflowId(source.getWorkflowId());
        target.setProposalId(source.getProposalId());
        target.setAgentId(source.getAgentId());
        target.setStatus(source.getStatus());
        target.setSeverity(source.getSeverity());
        target.setApprovalRequired(source.getApprovalRequired());
        target.setCreatedBy(source.getCreatedBy());
        target.setLastOperatorId(source.getLastOperatorId());
        target.setRollbackSuggestionsJson(source.getRollbackSuggestionsJson());
        target.setActionsJson(source.getActionsJson());
        target.setMetadataJson(source.getMetadataJson());
        target.setExecutionLeaseOwner(source.getExecutionLeaseOwner());
        target.setExecutionLeaseAcquiredAt(source.getExecutionLeaseAcquiredAt());
        target.setExecutionLeaseExpiresAt(source.getExecutionLeaseExpiresAt());
        target.setExecutionLeaseVersion(source.getExecutionLeaseVersion());
        target.setVersion(source.getVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private AgentRemediationWorkflowRecord toRecord(AgentRemediationWorkflowPo source) {
        if (source == null) return null;
        AgentRemediationWorkflowRecord target = new AgentRemediationWorkflowRecord();
        target.setWorkflowId(source.getWorkflowId());
        target.setProposalId(source.getProposalId());
        target.setAgentId(source.getAgentId());
        target.setStatus(source.getStatus());
        target.setSeverity(source.getSeverity());
        target.setApprovalRequired(source.getApprovalRequired());
        target.setCreatedBy(source.getCreatedBy());
        target.setLastOperatorId(source.getLastOperatorId());
        target.setRollbackSuggestionsJson(source.getRollbackSuggestionsJson());
        target.setActionsJson(source.getActionsJson());
        target.setMetadataJson(source.getMetadataJson());
        target.setExecutionLeaseOwner(source.getExecutionLeaseOwner());
        target.setExecutionLeaseAcquiredAt(source.getExecutionLeaseAcquiredAt());
        target.setExecutionLeaseExpiresAt(source.getExecutionLeaseExpiresAt());
        target.setExecutionLeaseVersion(source.getExecutionLeaseVersion());
        target.setVersion(source.getVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private AgentRemediationWorkflowHistoryPo toPo(AgentRemediationWorkflowHistoryRecord source) {
        if (source == null) return null;
        AgentRemediationWorkflowHistoryPo target = new AgentRemediationWorkflowHistoryPo();
        target.setHistoryId(source.getHistoryId());
        target.setWorkflowId(source.getWorkflowId());
        target.setAgentId(source.getAgentId());
        target.setEventType(source.getEventType());
        target.setOperatorId(source.getOperatorId());
        target.setReason(source.getReason());
        target.setMetadataJson(source.getMetadataJson());
        target.setOccurredAt(source.getOccurredAt());
        return target;
    }

    private AgentRemediationWorkflowHistoryRecord toRecord(AgentRemediationWorkflowHistoryPo source) {
        if (source == null) return null;
        AgentRemediationWorkflowHistoryRecord target = new AgentRemediationWorkflowHistoryRecord();
        target.setHistoryId(source.getHistoryId());
        target.setWorkflowId(source.getWorkflowId());
        target.setAgentId(source.getAgentId());
        target.setEventType(source.getEventType());
        target.setOperatorId(source.getOperatorId());
        target.setReason(source.getReason());
        target.setMetadataJson(source.getMetadataJson());
        target.setOccurredAt(source.getOccurredAt());
        return target;
    }

    private AgentRemediationWorkflowActionExecutionPo toPo(AgentRemediationWorkflowActionExecutionRecord source) {
        if (source == null) return null;
        AgentRemediationWorkflowActionExecutionPo target = new AgentRemediationWorkflowActionExecutionPo();
        target.setActionExecutionId(source.getActionExecutionId());
        target.setWorkflowId(source.getWorkflowId());
        target.setAgentId(source.getAgentId());
        target.setActionId(source.getActionId());
        target.setActionType(source.getActionType());
        target.setIdempotencyKey(source.getIdempotencyKey());
        target.setStatus(source.getStatus());
        target.setAttemptCount(source.getAttemptCount());
        target.setLastOperatorId(source.getLastOperatorId());
        target.setLastReason(source.getLastReason());
        target.setLastResultJson(source.getLastResultJson());
        target.setLastError(source.getLastError());
        target.setFirstAttemptAt(source.getFirstAttemptAt());
        target.setLastAttemptAt(source.getLastAttemptAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private AgentRemediationWorkflowActionExecutionRecord toRecord(AgentRemediationWorkflowActionExecutionPo source) {
        if (source == null) return null;
        AgentRemediationWorkflowActionExecutionRecord target = new AgentRemediationWorkflowActionExecutionRecord();
        target.setActionExecutionId(source.getActionExecutionId());
        target.setWorkflowId(source.getWorkflowId());
        target.setAgentId(source.getAgentId());
        target.setActionId(source.getActionId());
        target.setActionType(source.getActionType());
        target.setIdempotencyKey(source.getIdempotencyKey());
        target.setStatus(source.getStatus());
        target.setAttemptCount(source.getAttemptCount());
        target.setLastOperatorId(source.getLastOperatorId());
        target.setLastReason(source.getLastReason());
        target.setLastResultJson(source.getLastResultJson());
        target.setLastError(source.getLastError());
        target.setFirstAttemptAt(source.getFirstAttemptAt());
        target.setLastAttemptAt(source.getLastAttemptAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
