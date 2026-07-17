package com.opensocket.aievent.database.persistence.task.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.task.po.TaskDispatchAttemptPo;

@Mapper
public interface TaskDispatchAttemptDao {
    int upsert(@Param("attempt") TaskDispatchAttemptPo attempt);
    TaskDispatchAttemptPo findById(@Param("dispatchAttemptId") String dispatchAttemptId);
    List<TaskDispatchAttemptPo> findByTaskId(@Param("taskId") String taskId, @Param("limit") int limit);
    List<TaskDispatchAttemptPo> recent(@Param("limit") int limit);
}
