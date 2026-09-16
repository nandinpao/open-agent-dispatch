package com.opensocket.aievent.database.persistence.a2a.dao;
import org.apache.ibatis.annotations.*; import com.opensocket.aievent.database.persistence.a2a.po.A2AParentAggregationPo;
@Mapper public interface A2AParentAggregationDao { int upsert(@Param("aggregation") A2AParentAggregationPo aggregation); int updateExpectedVersion(@Param("aggregation") A2AParentAggregationPo aggregation,@Param("expectedVersion")long expectedVersion); A2AParentAggregationPo findByParentTask(@Param("tenantId")String tenantId,@Param("parentTaskId")String parentTaskId); }
