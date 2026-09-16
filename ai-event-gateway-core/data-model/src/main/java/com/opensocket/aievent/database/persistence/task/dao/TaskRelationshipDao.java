package com.opensocket.aievent.database.persistence.task.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.task.po.TaskRelationshipPo;

@Mapper
public interface TaskRelationshipDao {
    int insert(@Param("relationship") TaskRelationshipPo relationship);
    TaskRelationshipPo findById(@Param("tenantId") String tenantId, @Param("relationshipId") String relationshipId);
    TaskRelationshipPo findByIdempotencyKey(@Param("tenantId") String tenantId, @Param("idempotencyKey") String idempotencyKey);
    TaskRelationshipPo findNatural(@Param("tenantId") String tenantId, @Param("fromTaskId") String fromTaskId, @Param("toTaskId") String toTaskId, @Param("relationshipType") String relationshipType);
    List<TaskRelationshipPo> findOutbound(@Param("tenantId") String tenantId, @Param("taskId") String taskId, @Param("limit") int limit);
    List<TaskRelationshipPo> findInbound(@Param("tenantId") String tenantId, @Param("taskId") String taskId, @Param("limit") int limit);
    int delete(@Param("tenantId") String tenantId, @Param("relationshipId") String relationshipId);
}
