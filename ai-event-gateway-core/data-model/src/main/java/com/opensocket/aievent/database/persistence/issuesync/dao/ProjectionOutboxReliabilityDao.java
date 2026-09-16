package com.opensocket.aievent.database.persistence.issuesync.dao;
import java.time.OffsetDateTime;import java.util.*;import org.apache.ibatis.annotations.*;import com.opensocket.aievent.database.persistence.issuesync.po.*;
@Mapper public interface ProjectionOutboxReliabilityDao {
 int insert(@Param("value") ProjectionOutboxReliabilityPo value);
 int updateCas(@Param("value") ProjectionOutboxReliabilityPo value,@Param("expectedVersion") long expectedVersion);
 ProjectionOutboxReliabilityPo find(@Param("tenantId")String tenantId,@Param("outboxId")String outboxId);
 List<ProjectionOutboxReliabilityPo> claimDue(@Param("workerId")String workerId,@Param("now")OffsetDateTime now,@Param("claimUntil")OffsetDateTime claimUntil,@Param("limit")int limit);
 int heartbeat(@Param("tenantId")String tenantId,@Param("outboxId")String outboxId,@Param("workerId")String workerId,@Param("claimTokenHash")String claimTokenHash,@Param("expectedVersion")long expectedVersion,@Param("heartbeatAt")OffsetDateTime heartbeatAt,@Param("claimUntil")OffsetDateTime claimUntil);
 List<ProjectionOutboxReliabilityPo> list(@Param("tenantId")String tenantId,@Param("status")String status,@Param("limit")int limit);
 int insertAttempt(@Param("value")ProjectionProviderAttemptEvidencePo value); List<ProjectionProviderAttemptEvidencePo> listAttempts(@Param("tenantId")String tenantId,@Param("outboxId")String outboxId,@Param("limit")int limit);
 int insertReadback(@Param("value")ProjectionReadbackEvidencePo value); List<ProjectionReadbackEvidencePo> listReadbacks(@Param("tenantId")String tenantId,@Param("outboxId")String outboxId,@Param("limit")int limit);
}
