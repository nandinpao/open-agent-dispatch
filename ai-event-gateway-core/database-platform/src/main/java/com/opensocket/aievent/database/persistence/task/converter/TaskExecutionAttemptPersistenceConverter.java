package com.opensocket.aievent.database.persistence.task.converter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.executionattempt.TaskExecutionAttempt;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttemptStatus;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.database.persistence.task.po.TaskExecutionAttemptPo;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "assignment", name = "store", havingValue = "MYBATIS")
public class TaskExecutionAttemptPersistenceConverter {
    public TaskExecutionAttemptPo toPo(TaskExecutionAttempt attempt) {
        TaskExecutionAttemptPo po = new TaskExecutionAttemptPo();
        po.setExecutionAttemptId(attempt.getExecutionAttemptId());
        po.setTaskId(attempt.getTaskId());
        po.setAssignmentId(attempt.getAssignmentId());
        
        po.setDispatchAttemptId(attempt.getDispatchAttemptId());
        po.setAgentId(attempt.getAgentId());
        po.setAgentSessionId(attempt.getAgentSessionId());
        po.setLeaseId(attempt.getLeaseId());
        po.setFencingToken(attempt.getFencingToken());
        po.setAttemptNo(attempt.getAttemptNo());
        po.setStatus(attempt.getStatus() == null ? null : attempt.getStatus().name());
        po.setResultCode(attempt.getResultCode());
        po.setErrorCode(attempt.getErrorCode());
        po.setErrorMessage(attempt.getErrorMessage());
        po.setCallbackId(attempt.getCallbackId());
        po.setCreatedAt(attempt.getCreatedAt());
        po.setStartedAt(attempt.getStartedAt());
        po.setCompletedAt(attempt.getCompletedAt());
        po.setUpdatedAt(attempt.getUpdatedAt());
        return po;
    }

    public TaskExecutionAttempt toDomain(TaskExecutionAttemptPo po) {
        TaskExecutionAttempt attempt = new TaskExecutionAttempt();
        attempt.setExecutionAttemptId(po.getExecutionAttemptId());
        attempt.setTaskId(po.getTaskId());
        attempt.setAssignmentId(po.getAssignmentId());
        
        attempt.setDispatchAttemptId(po.getDispatchAttemptId());
        attempt.setAgentId(po.getAgentId());
        attempt.setAgentSessionId(po.getAgentSessionId());
        attempt.setLeaseId(po.getLeaseId());
        attempt.setFencingToken(po.getFencingToken());
        attempt.setAttemptNo(po.getAttemptNo());
        attempt.setStatus(po.getStatus() == null ? null : TaskExecutionAttemptStatus.valueOf(po.getStatus()));
        attempt.setResultCode(po.getResultCode());
        attempt.setErrorCode(po.getErrorCode());
        attempt.setErrorMessage(po.getErrorMessage());
        attempt.setCallbackId(po.getCallbackId());
        attempt.setCreatedAt(po.getCreatedAt());
        attempt.setStartedAt(po.getStartedAt());
        attempt.setCompletedAt(po.getCompletedAt());
        attempt.setUpdatedAt(po.getUpdatedAt());
        return attempt;
    }
}
