package com.opensocket.aievent.database.persistence.issue.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.issue.po.TaskIssueLinkPo;

@Mapper
public interface TaskIssueLinkDao {
    int upsert(@Param("link") TaskIssueLinkPo link);
    TaskIssueLinkPo findByTaskId(@Param("taskId") String taskId);
    List<TaskIssueLinkPo> findByTaskIds(@Param("taskIds") List<String> taskIds);
    List<TaskIssueLinkPo> recent(@Param("limit") int limit);
}
