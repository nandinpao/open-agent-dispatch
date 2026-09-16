package com.opensocket.aievent.database.persistence.task.dao;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.task.po.TaskStateHistoryPo;

@Mapper
public interface TaskStateHistoryDao {
    int insert(@Param("entry") TaskStateHistoryPo entry);
    List<TaskStateHistoryPo> findByTask(@Param("tenantId") String tenantId, @Param("taskId") String taskId, @Param("limit") int limit);
    Optional<TaskStateHistoryPo> findByIdempotencyKey(@Param("tenantId") String tenantId, @Param("idempotencyKey") String idempotencyKey);
}
