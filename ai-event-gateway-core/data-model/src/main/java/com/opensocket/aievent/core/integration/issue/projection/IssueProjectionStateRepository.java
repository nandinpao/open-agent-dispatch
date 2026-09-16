package com.opensocket.aievent.core.integration.issue.projection;

import com.opensocket.aievent.core.issuetracking.contract.ProjectionLifecycleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface IssueProjectionStateRepository {
    IssueProjectionState save(IssueProjectionState value);
    Optional<IssueProjectionState> find(String tenantId,String projectionId);
    Optional<IssueProjectionState> findByAggregateKey(String tenantId,String aggregateKey);
    Optional<IssueProjectionState> findBySourceEvent(String tenantId,String sourceEventId);
    Optional<IssueProjectionState> findByTaskIssueLink(String tenantId,String taskIssueLinkId);
    Optional<IssueProjectionState> findLatestByTask(String tenantId,String taskId);
    List<IssueProjectionState> list(String tenantId,ProjectionLifecycleStatus state,int limit);
    List<IssueProjectionState> listDue(OffsetDateTime now,int limit);
    boolean tryRecordDomainEvent(String tenantId,String projectionId,String domainEventId,long operationSequence,String payloadHash,OffsetDateTime appliedAt);
    IssueProjectionReconciliationCase saveCase(IssueProjectionReconciliationCase value);
    Optional<IssueProjectionReconciliationCase> findCase(String tenantId,String caseId);
    Optional<IssueProjectionReconciliationCase> findOpenCaseByProjection(String tenantId,String projectionId);
    List<IssueProjectionReconciliationCase> listCases(String tenantId,IssueProjectionReconciliationStatus status,int limit);
    default String mode(){return "CUSTOM";}
}
