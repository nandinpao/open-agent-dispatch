package com.opensocket.aievent.database.persistence.task.dao;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.task.po.TaskAssignmentPo;

@Mapper
public interface TaskAssignmentDao {
    int upsert(@Param("assignment") TaskAssignmentPo assignment);
    TaskAssignmentPo findById(@Param("assignmentId") String assignmentId);
    TaskAssignmentPo findOpenByTaskId(@Param("taskId") String taskId);
    int releaseCapacityReservation(@Param("assignmentId") String assignmentId, @Param("releasedAt") OffsetDateTime releasedAt);
    List<TaskAssignmentPo> findByTaskId(@Param("taskId") String taskId, @Param("limit") int limit);
    List<TaskAssignmentPo> recent(@Param("limit") int limit);
}
