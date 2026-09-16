package com.opensocket.aievent.database.persistence.task.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.task.po.TaskPo;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlan;

@Mapper
public interface TaskDao {
    int upsert(@Param("task") TaskPo task);
    int insert(@Param("task") TaskPo task);
    TaskPo findById(@Param("taskId") String taskId);
    TaskPo findByTenantAndId(@Param("tenantId") String tenantId, @Param("taskId") String taskId);
    List<TaskPo> findByRootTaskId(@Param("tenantId") String tenantId, @Param("rootTaskId") String rootTaskId, @Param("limit") int limit);
    List<TaskPo> findByParentTaskId(@Param("tenantId") String tenantId, @Param("parentTaskId") String parentTaskId, @Param("limit") int limit);
    int transferOwnership(@Param("tenantId") String tenantId,
                          @Param("taskId") String taskId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("ownerDepartmentId") String ownerDepartmentId,
                          @Param("ownerGroupId") String ownerGroupId,
                          @Param("actorId") String actorId,
                          @Param("reason") String reason,
                          @Param("correlationId") String correlationId,
                          @Param("changedAt") OffsetDateTime changedAt);
    int transitionGovernanceState(@Param("tenantId") String tenantId,
                                  @Param("taskId") String taskId,
                                  @Param("expectedVersion") long expectedVersion,
                                  @Param("newStatus") String newStatus,
                                  @Param("reasonCode") String reasonCode,
                                  @Param("reason") String reason,
                                  @Param("actorType") String actorType,
                                  @Param("actorId") String actorId,
                                  @Param("correlationId") String correlationId,
                                  @Param("idempotencyKey") String idempotencyKey,
                                  @Param("transitionAt") OffsetDateTime transitionAt);
    TaskPo findOpenByIncidentAndType(@Param("incidentId") String incidentId, @Param("taskType") String taskType);
    TaskPo findOpenByTenantAndIncidentAndType(@Param("tenantId") String tenantId, @Param("incidentId") String incidentId, @Param("taskType") String taskType);
    List<TaskPo> findByIncidentId(@Param("incidentId") String incidentId, @Param("limit") int limit);
    List<TaskPo> findByTenantAndIncidentId(@Param("tenantId") String tenantId, @Param("incidentId") String incidentId, @Param("limit") int limit);
    List<TaskPo> findOpenUpdatedBefore(@Param("cutoff") OffsetDateTime cutoff, @Param("limit") int limit);
    List<TaskPo> findByStatusUpdatedBefore(@Param("status") String status, @Param("cutoff") OffsetDateTime cutoff, @Param("limit") int limit);
    List<TaskPo> claimDispatchRecoveryDue(@Param("workerId") String workerId,
                                           @Param("now") OffsetDateTime now,
                                           @Param("claimUntil") OffsetDateTime claimUntil,
                                           @Param("limit") int limit);
    int suspendDispatchUntilConfigurationChange(@Param("taskId") String taskId,
                                                @Param("blockerCode") String blockerCode,
                                                @Param("reason") String reason,
                                                @Param("now") OffsetDateTime now);
    int wakeConfigurationBlockedTasks(@Param("tenantId") String tenantId,
                                      @Param("sourceSystem") String sourceSystem,
                                      @Param("now") OffsetDateTime now,
                                      @Param("reason") String reason);
    int clearDispatchRecoveryClaim(@Param("taskId") String taskId,
                                   @Param("workerId") String workerId,
                                   @Param("claimUntil") OffsetDateTime claimUntil,
                                   @Param("now") OffsetDateTime now);
    int transitionExecutionState(
            @Param("taskId") String taskId,
            @Param("allowedStatuses") List<String> allowedStatuses,
            @Param("newStatus") String newStatus,
            @Param("timeoutAt") OffsetDateTime timeoutAt,
            @Param("terminalAt") OffsetDateTime terminalAt,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("lifecycleReason") String lifecycleReason);
    List<TaskPo> searchAuthorized(@Param("incidentId") String incidentId,
                         @Param("tenantId") String tenantId,
                         @Param("siteId") String siteId,
                         @Param("plantId") String plantId,
                         @Param("taskType") String taskType,
                         @Param("status") String status,
                         @Param("beforeCreatedAt") OffsetDateTime beforeCreatedAt,
                         @Param("beforeTaskId") String beforeTaskId,
                         @Param("limit") int limit,
                         @Param("plan") TaskScopeQueryPlan plan);
    List<TaskPo> searchAuthorizedTarget(@Param("incidentId") String incidentId,
                         @Param("tenantId") String tenantId,
                         @Param("siteId") String siteId,
                         @Param("plantId") String plantId,
                         @Param("taskType") String taskType,
                         @Param("status") String status,
                         @Param("beforeCreatedAt") OffsetDateTime beforeCreatedAt,
                         @Param("beforeTaskId") String beforeTaskId,
                         @Param("limit") int limit,
                         @Param("plan") TaskScopeQueryPlan plan);
    List<TaskPo> search(@Param("incidentId") String incidentId,
                         @Param("tenantId") String tenantId,
                         @Param("siteId") String siteId,
                         @Param("plantId") String plantId,
                         @Param("taskType") String taskType,
                         @Param("status") String status,
                         @Param("beforeCreatedAt") OffsetDateTime beforeCreatedAt,
                         @Param("beforeTaskId") String beforeTaskId,
                         @Param("limit") int limit);
}
