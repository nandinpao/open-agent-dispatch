package com.opensocket.aievent.database.persistence.a2a.dao;
import java.time.OffsetDateTime; import java.util.List; import org.apache.ibatis.annotations.Mapper; import org.apache.ibatis.annotations.Param; import com.opensocket.aievent.database.persistence.a2a.po.A2AReconciliationCasePo;
@Mapper public interface A2AReconciliationCaseDao {
 int upsert(@Param("value")A2AReconciliationCasePo value);
 int updateExpectedVersion(@Param("value")A2AReconciliationCasePo value,@Param("expectedVersion")long expectedVersion);
 A2AReconciliationCasePo findById(@Param("tenantId")String tenantId,@Param("caseId")String caseId);
 A2AReconciliationCasePo findOpenByCancellation(@Param("tenantId")String tenantId,@Param("cancellationId")String cancellationId);
 A2AReconciliationCasePo findOpenByRequest(@Param("tenantId")String tenantId,@Param("requestId")String requestId);
 List<A2AReconciliationCasePo> findOpen(@Param("tenantId")String tenantId,@Param("limit")int limit);
 List<A2AReconciliationCasePo> claimDue(@Param("workerId")String workerId,@Param("now")OffsetDateTime now,@Param("claimUntil")OffsetDateTime claimUntil,@Param("limit")int limit);
 List<A2AReconciliationCasePo> claimExecutable(@Param("workerId")String workerId,@Param("now")OffsetDateTime now,@Param("claimUntil")OffsetDateTime claimUntil,@Param("limit")int limit);
 int heartbeat(@Param("tenantId")String tenantId,@Param("caseId")String caseId,@Param("workerId")String workerId,@Param("expectedVersion")long expectedVersion,@Param("heartbeatAt")OffsetDateTime heartbeatAt,@Param("claimUntil")OffsetDateTime claimUntil);
 List<A2AReconciliationCasePo> search(@Param("tenantId")String tenantId,@Param("status")String status,@Param("caseType")String caseType,@Param("authorityOwner")String authorityOwner,@Param("requestId")String requestId,@Param("text")String text,@Param("offset")int offset,@Param("limit")int limit,@Param("sortDirection")String sortDirection);
 long count(@Param("tenantId")String tenantId,@Param("status")String status,@Param("caseType")String caseType,@Param("authorityOwner")String authorityOwner,@Param("requestId")String requestId,@Param("text")String text);
}
