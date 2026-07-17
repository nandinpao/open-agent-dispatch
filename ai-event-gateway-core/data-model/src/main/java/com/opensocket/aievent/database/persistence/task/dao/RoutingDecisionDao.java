package com.opensocket.aievent.database.persistence.task.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.task.po.RoutingDecisionPo;

@Mapper
public interface RoutingDecisionDao {
    int upsert(@Param("decision") RoutingDecisionPo decision);
    RoutingDecisionPo findById(@Param("decisionId") String decisionId);
    List<RoutingDecisionPo> findByTaskId(@Param("taskId") String taskId, @Param("limit") int limit);
    List<RoutingDecisionPo> recent(@Param("limit") int limit);
}
