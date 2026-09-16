package com.opensocket.aievent.database.persistence.issueprojection.dao;
import java.time.OffsetDateTime; import java.util.List; import org.apache.ibatis.annotations.*; import com.opensocket.aievent.database.persistence.issueprojection.po.*;
@Mapper public interface IssueRelayDao {
 int insertRelay(@Param("value")CrossProjectIssueRelayPo value); int updateRelayState(@Param("value")CrossProjectIssueRelayPo value); CrossProjectIssueRelayPo findRelay(@Param("tenantId")String tenantId,@Param("relayId")String relayId); CrossProjectIssueRelayPo findRelayByIdempotencyKey(@Param("tenantId")String tenantId,@Param("idempotencyKey")String idempotencyKey); List<CrossProjectIssueRelayPo> listRelays(@Param("tenantId")String tenantId,@Param("taskId")String taskId,@Param("limit")int limit); List<CrossProjectIssueRelayPo> listDueRelays(@Param("now")OffsetDateTime now,@Param("limit")int limit);
 int insertRelayAttempt(@Param("value")IssueRelayAttemptPo value); List<IssueRelayAttemptPo> listRelayAttempts(@Param("tenantId")String tenantId,@Param("relayId")String relayId,@Param("limit")int limit);
}
