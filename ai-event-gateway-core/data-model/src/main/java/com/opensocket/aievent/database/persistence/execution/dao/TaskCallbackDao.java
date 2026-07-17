package com.opensocket.aievent.database.persistence.execution.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.execution.po.TaskCallbackPo;

@Mapper
public interface TaskCallbackDao {
    int upsert(@Param("record") TaskCallbackPo record);
    int insert(@Param("record") TaskCallbackPo record);
    TaskCallbackPo findByCallbackId(@Param("callbackId") String callbackId);
    List<TaskCallbackPo> findByTaskId(@Param("taskId") String taskId, @Param("limit") int limit);
    List<TaskCallbackPo> findByDispatchRequestId(@Param("dispatchRequestId") String dispatchRequestId, @Param("limit") int limit);
    List<TaskCallbackPo> recent(@Param("limit") int limit);
}
