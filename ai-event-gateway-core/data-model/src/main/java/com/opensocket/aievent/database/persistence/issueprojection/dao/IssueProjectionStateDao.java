package com.opensocket.aievent.database.persistence.issueprojection.dao;
import java.time.OffsetDateTime; import java.util.List; import org.apache.ibatis.annotations.*; import com.opensocket.aievent.database.persistence.issueprojection.po.*;
@Mapper public interface IssueProjectionStateDao {
 int insertState(@Param("value")IssueProjectionStatePo value);
 int updateStateCas(@Param("value")IssueProjectionStatePo value,@Param("expectedVersion")long expectedVersion);
 IssueProjectionStatePo find(@Param("tenantId")String tenantId,@Param("projectionId")String projectionId);
 IssueProjectionStatePo findByAggregateKey(@Param("tenantId")String tenantId,@Param("aggregateKey")String aggregateKey);
 IssueProjectionStatePo findBySourceEvent(@Param("tenantId")String tenantId,@Param("sourceEventId")String sourceEventId);
 IssueProjectionStatePo findByTaskIssueLink(@Param("tenantId")String tenantId,@Param("taskIssueLinkId")String taskIssueLinkId);
 IssueProjectionStatePo findLatestByTask(@Param("tenantId")String tenantId,@Param("taskId")String taskId);
 List<IssueProjectionStatePo> list(@Param("tenantId")String tenantId,@Param("state")String state,@Param("limit")int limit);
 List<IssueProjectionStatePo> listDue(@Param("now")OffsetDateTime now,@Param("limit")int limit);
 int insertEventReceipt(@Param("tenantId")String tenantId,@Param("projectionId")String projectionId,@Param("domainEventId")String domainEventId,@Param("operationSequence")long operationSequence,@Param("payloadHash")String payloadHash,@Param("appliedAt")OffsetDateTime appliedAt);
 int insertCase(@Param("value")IssueProjectionReconciliationCasePo value); int updateCase(@Param("value")IssueProjectionReconciliationCasePo value); IssueProjectionReconciliationCasePo findCase(@Param("tenantId")String tenantId,@Param("caseId")String caseId); IssueProjectionReconciliationCasePo findOpenCaseByProjection(@Param("tenantId")String tenantId,@Param("projectionId")String projectionId); List<IssueProjectionReconciliationCasePo> listCases(@Param("tenantId")String tenantId,@Param("status")String status,@Param("limit")int limit);
}
