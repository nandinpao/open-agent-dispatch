package com.opensocket.aievent.database.persistence.issue.dao;
import java.util.List; import org.apache.ibatis.annotations.*; import com.opensocket.aievent.database.persistence.issue.po.TaskIssueLinkPo;
@Mapper
public interface TaskIssueLinkDao {
 int upsert(@Param("link") TaskIssueLinkPo link);
 TaskIssueLinkPo findByTenantAndLinkId(@Param("tenantId") String tenantId,@Param("linkId") String linkId);
 TaskIssueLinkPo findByTenantAndIdempotencyKey(@Param("tenantId") String tenantId,@Param("idempotencyKey") String key);
 TaskIssueLinkPo findByExternalIssue(@Param("tenantId")String tenantId,@Param("connectionId")String connectionId,@Param("externalProjectId")String projectId,@Param("externalIssueId")String issueId);
 List<TaskIssueLinkPo> findAllByTenantAndTaskId(@Param("tenantId") String tenantId,@Param("taskId") String taskId);
 List<TaskIssueLinkPo> findAllByTenantAndTaskIds(@Param("tenantId") String tenantId,@Param("taskIds") List<String> taskIds);
 List<TaskIssueLinkPo> recent(@Param("tenantId") String tenantId,@Param("limit") int limit);
}
