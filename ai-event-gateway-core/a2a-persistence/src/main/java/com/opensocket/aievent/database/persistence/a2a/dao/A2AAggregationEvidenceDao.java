package com.opensocket.aievent.database.persistence.a2a.dao;
import java.util.List; import org.apache.ibatis.annotations.*; import com.opensocket.aievent.database.persistence.a2a.po.A2AAggregationEvidencePo;
@Mapper public interface A2AAggregationEvidenceDao { int insert(@Param("evidence")A2AAggregationEvidencePo evidence); List<A2AAggregationEvidencePo> findByParentTask(@Param("tenantId")String tenantId,@Param("parentTaskId")String parentTaskId,@Param("limit")int limit); }
