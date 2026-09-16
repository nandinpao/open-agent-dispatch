package com.opensocket.aievent.database.persistence.task.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.task.po.TaskLineageEvidencePo;
import com.opensocket.aievent.core.task.lineage.TaskLineageQuery;

@Mapper
public interface TaskLineageEvidenceDao {
    int insert(@Param("evidence") TaskLineageEvidencePo evidence);
    List<TaskLineageEvidencePo> findByTask(@Param("tenantId") String tenantId,@Param("taskId") String taskId,@Param("limit") int limit);
    List<TaskLineageEvidencePo> findByRootTask(@Param("tenantId") String tenantId,@Param("rootTaskId") String rootTaskId,@Param("limit") int limit);
    List<TaskLineageEvidencePo> findByAgent(@Param("tenantId") String tenantId,@Param("agentId") String agentId,@Param("limit") int limit);
    List<TaskLineageEvidencePo> findByCorrelation(@Param("tenantId") String tenantId,@Param("correlationId") String correlationId,@Param("limit") int limit);
    List<TaskLineageEvidencePo> search(@Param("query") TaskLineageQuery query);
}
