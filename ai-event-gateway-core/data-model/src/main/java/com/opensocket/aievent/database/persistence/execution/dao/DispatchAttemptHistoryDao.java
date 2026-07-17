package com.opensocket.aievent.database.persistence.execution.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.execution.po.DispatchAttemptHistoryPo;

@Mapper
public interface DispatchAttemptHistoryDao {
    int insert(@Param("record") DispatchAttemptHistoryPo record);
    List<DispatchAttemptHistoryPo> findByTaskId(@Param("taskId") String taskId, @Param("limit") int limit);
    List<DispatchAttemptHistoryPo> findByDispatchRequestId(@Param("dispatchRequestId") String dispatchRequestId, @Param("limit") int limit);
    List<DispatchAttemptHistoryPo> recent(@Param("limit") int limit);
    List<DispatchAttemptHistoryPo> findSince(@Param("since") OffsetDateTime since, @Param("limit") int limit);
}
