package com.opensocket.aievent.database.persistence.a2a.dao;
import java.time.OffsetDateTime; import java.util.List; import org.apache.ibatis.annotations.*; import com.opensocket.aievent.database.persistence.a2a.po.A2AResultProcessingPo;
@Mapper public interface A2AResultProcessingDao {
 int insert(@Param("processing") A2AResultProcessingPo processing);
 int updateExpectedVersion(@Param("processing") A2AResultProcessingPo processing,@Param("expectedVersion")long expectedVersion);
 A2AResultProcessingPo findByResult(@Param("tenantId")String tenantId,@Param("resultId")String resultId);
 List<A2AResultProcessingPo> findDue(@Param("dueAt")OffsetDateTime dueAt,@Param("limit")int limit);
 List<A2AResultProcessingPo> findByTask(@Param("tenantId")String tenantId,@Param("taskId")String taskId,@Param("limit")int limit);
}
