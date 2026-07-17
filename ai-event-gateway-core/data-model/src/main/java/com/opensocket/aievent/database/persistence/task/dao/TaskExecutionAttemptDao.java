package com.opensocket.aievent.database.persistence.task.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.task.po.TaskExecutionAttemptPo;

@Mapper
public interface TaskExecutionAttemptDao {
    int upsert(@Param("attempt") TaskExecutionAttemptPo attempt);
    TaskExecutionAttemptPo findById(@Param("executionAttemptId") String executionAttemptId);
    TaskExecutionAttemptPo findCurrentByAssignmentId(@Param("assignmentId") String assignmentId);
    List<TaskExecutionAttemptPo> findByTaskId(@Param("taskId") String taskId, @Param("limit") int limit);
    int countByTaskId(@Param("taskId") String taskId);
}
