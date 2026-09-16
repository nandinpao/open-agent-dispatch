package com.opensocket.aievent.core.a2a.authority;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.a2a.A2APolicy;
import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.application.port.out.A2ATaskAuthorityOperations;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;
import com.opensocket.aievent.core.task.domain.TaskActorType;
import com.opensocket.aievent.core.task.domain.TaskDomainService;
import com.opensocket.aievent.core.task.domain.TaskStateTransitionCommand;

@Component
public class DefaultA2ATaskAuthorityAdapter implements A2ATaskAuthorityOperations {
    private static final Logger log = LoggerFactory.getLogger(DefaultA2ATaskAuthorityAdapter.class);
    private static final String REQUIRED_BEFORE_DISPATCH = "REQUIRED_BEFORE_DISPATCH";

    private final TaskRepository tasks;
    private final TaskDomainService taskDomain;
    private final TaskAssignmentRepository assignments;

    public DefaultA2ATaskAuthorityAdapter(TaskRepository tasks, TaskDomainService taskDomain,
                                          TaskAssignmentRepository assignments) {
        this.tasks = tasks;
        this.taskDomain = taskDomain;
        this.assignments = assignments;
    }

    @Override
    public TaskRecord requireTask(String tenantId, String taskId) {
        return tasks.findByTenantAndId(tenantId.trim(), taskId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    @Override
    public boolean isCurrentAssignedAgent(String tenantId, String taskId, String agentId) {
        return assignments.findOpenByTenantAndTaskId(tenantId, taskId)
                .filter(a -> tenantId.equals(a.getTenantId()) && agentId.equals(a.getAgentId())
                        && a.getStatus() == AssignmentStatus.ASSIGNED)
                .isPresent();
    }

    @Override
    public List<TaskRecord> findChain(String tenantId, String rootTaskId, int limit) {
        return tasks.findByRootTaskId(tenantId, rootTaskId, limit);
    }

    @Override
    public ChildTaskReceipt createChildTask(CreateChildTaskRequest command) {
        TaskRecord source = requireTask(command.tenantId(), command.parentTaskId());
        TaskRecord existing = tasks.findByRootTaskId(command.tenantId(), command.rootTaskId(), 1000).stream()
                .filter(t -> command.idempotencyKey().equals(t.getCreationIdempotencyKey()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            log.info("a2a_child_task_authority_replayed parentTaskId={} childTaskId={} parentIssueSyncPolicy={} childIssueSyncPolicy={} childIssueSyncPolicySource={} inheritanceMode={} inheritedFromTaskId={} childMatchedFlowId={} childMatchedRuleId={} routingPath={}",
                    source.getTaskId(), existing.getTaskId(), source.getIssueSyncPolicy(), existing.getIssueSyncPolicy(), existing.getIssueSyncPolicySource(),
                    existing.getIssueSyncPolicyInheritanceMode(), existing.getIssueSyncPolicyInheritedFromTaskId(), existing.getMatchedFlowId(), existing.getMatchedRuleId(), existing.getRoutingPath());
            return new ChildTaskReceipt(existing.getTaskId(), existing.getVersion(), true);
        }

        log.info("a2a_child_task_authority_resolving parentTaskId={} parentMatchedFlowId={} parentMatchedRuleId={} parentIssueSyncPolicy={} parentIssueSyncPolicySource={} a2aPolicyId={} targetDomainId={} targetAgentPoolId={} contract=A2A_PARENT_ISSUE_POLICY_INHERITANCE",
                source.getTaskId(), source.getMatchedFlowId(), source.getMatchedRuleId(), source.getIssueSyncPolicy(), source.getIssueSyncPolicySource(),
                command.a2aPolicyId(), command.targetDomainId(), command.targetAgentPoolId());

        TaskRecord child = new TaskRecord();
        child.setTaskId("task-" + UUID.randomUUID());
        child.setTaskKey("A2A-" + command.a2aRequestId());
        child.setTitle(command.taskType() + " for " + source.getTaskKey());
        child.setDescription(firstNonBlank(command.reason(), "A2A delegated child task"));
        child.setIncidentId(source.getIncidentId());
        child.setSourceEventId(source.getSourceEventId());
        child.setSourceSystem(firstNonBlank(source.getExecutorDomainId(), source.getSourceSystem()));
        child.setEventStage("A2A");
        child.setOriginSourceSystem(firstNonBlank(source.getOriginSourceSystem(), source.getSourceSystem()));
        child.setTargetSystem(command.targetDomainId());
        child.setTaskType(TaskType.RESOLUTION);
        child.setTaskTypeCode(command.taskType());
        child.setRequiredCapabilities(command.requestedCapabilityCodes());
        child.setStatus(REQUIRED_BEFORE_DISPATCH.equals(command.handoffContextRequirement())
                ? TaskStatus.WAITING_CONTEXT
                : TaskStatus.QUEUED);
        child.setPriority(source.getPriority() == null ? TaskPriority.MEDIUM : source.getPriority());
        child.setTenantId(command.tenantId());
        child.setSiteId(source.getSiteId());
        child.setPlantId(source.getPlantId());
        child.setObjectType(source.getObjectType());
        child.setObjectId(source.getObjectId());
        child.setEventType(source.getEventType());
        child.setErrorCode(source.getErrorCode());
        child.setCorrelationId(command.correlationId());
        child.setRootTaskId(command.rootTaskId());
        child.setParentTaskId(command.parentTaskId());
        child.setSourceTaskId(command.parentTaskId());
        child.setRequestingTaskId(firstNonBlank(command.requestingTaskId(), command.parentTaskId()));
        child.setRequestingAgentId(command.requestingAgentId());
        child.setOwnerDepartmentId(firstNonBlank(command.targetDepartmentId(), "UNASSIGNED"));
        child.setOwnerGroupId(command.targetGroupId());
        child.setRequesterDepartmentId(source.getExecutorDepartmentId());
        child.setRequesterGroupId(source.getExecutorGroupId());
        child.setRequesterDomainId(source.getExecutorDomainId());
        child.setExecutorDepartmentId(firstNonBlank(command.targetDepartmentId(), "UNASSIGNED"));
        child.setExecutorGroupId(command.targetGroupId());
        child.setExecutorDomainId(command.targetDomainId());
        child.setSensitivityLevel(command.sensitivityLevel());
        child.setAssignedPoolId(command.targetAgentPoolId());
        child.setTargetPoolId(command.targetAgentPoolId());
        child.setA2aPolicyId(command.a2aPolicyId());
        child.setHopCount(command.hopCount());
        child.setResultAggregationPolicy(command.resultAggregationPolicy());
        child.setChildCancellationPolicy(command.childCancellationPolicy());
        child.setChildFailurePolicy(command.childFailurePolicy());
        // HF6 authority contract: A2A direct-to-pool routing does not run the Flow/Rule matcher.
        // Do not copy parent matchedFlowId/matchedRuleId because that would fabricate child routing authority.
        // The effective Issue policy is inherited explicitly from the parent Task instead of silently
        // accepting TaskRecord's OPTIONAL initializer.
        child.setIssueSyncPolicy(source.getIssueSyncPolicy());
        child.setIssueSyncPolicySource("A2A_PARENT_INHERITED");
        child.setIssueSyncPolicyInheritanceMode("A2A_PARENT");
        child.setIssueSyncPolicyInheritedFromTaskId(source.getTaskId());
        child.setMatchedFlowId(null);
        child.setMatchedRuleId(null);
        child.setRoutingPolicy("GOVERNED_POOL");
        child.setRoutingPath("A2A_POLICY_TO_AGENT_POOL");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        child.inheritOriginProvenance(source, "AGENT", firstNonBlank(command.requestingAgentId(), "A2A_CORE"), now);
        child.setCreationIdempotencyKey(command.idempotencyKey());
        child.setCreatedReason("A2A Child Task requested through Task Authority Port; policyId=" + safe(command.a2aPolicyId())
                + "; requestedServiceCode=" + safe(command.requestedServiceCode())
                + "; handoffContextPolicyId=" + safe(command.handoffContextPolicyId())
                + "; contextRequirement=" + safe(command.handoffContextRequirement()));
        child.setCreatedAt(now);
        child.setUpdatedAt(now);
        child.setVersion(1L);

        TaskRecord saved = tasks.save(child);
        log.info("a2a_child_task_authority_resolved parentTaskId={} childTaskId={} parentIssueSyncPolicy={} parentIssueSyncPolicySource={} childIssueSyncPolicy={} childIssueSyncPolicySource={} inheritanceMode={} inheritedFromTaskId={} childMatchedFlowId={} childMatchedRuleId={} routingPath={} a2aPolicyId={}",
                source.getTaskId(), saved.getTaskId(), source.getIssueSyncPolicy(), source.getIssueSyncPolicySource(), saved.getIssueSyncPolicy(),
                saved.getIssueSyncPolicySource(), saved.getIssueSyncPolicyInheritanceMode(), saved.getIssueSyncPolicyInheritedFromTaskId(),
                saved.getMatchedFlowId(), saved.getMatchedRuleId(), saved.getRoutingPath(), saved.getA2aPolicyId());
        return new ChildTaskReceipt(saved.getTaskId(), saved.getVersion(), false);
    }

    @Override
    public TaskRecord createChildTask(A2ARequest request, TaskRecord source, A2APolicy policy) {
        ChildTaskReceipt receipt = createChildTask(new CreateChildTaskRequest(
                request.getTenantId(),
                request.getRequestId(),
                source.getTaskId(),
                request.getRootTaskId(),
                request.getRequestingTaskId(),
                request.getRequestingAgentId(),
                request.getTargetDepartmentId(),
                request.getTargetGroupId(),
                request.getTargetDomainId(),
                request.getTargetAgentPoolId(),
                request.getRequestedTaskType(),
                request.getRequestedServiceCode(),
                request.getRequestedCapabilityCodes(),
                request.getSensitivityLevel(),
                request.getHopCount(),
                policy.getPolicyId(),
                policy.getHandoffContextPolicyId(),
                policy.getHandoffContextRequirement(),
                policy.getResultAggregationPolicy() == null ? null : policy.getResultAggregationPolicy().name(),
                policy.getCancellationPolicy() == null ? null : policy.getCancellationPolicy().name(),
                policy.getFailurePropagationPolicy() == null ? null : policy.getFailurePropagationPolicy().name(),
                request.getReason(),
                "a2a-child:" + request.getIdempotencyKey(),
                request.getCorrelationId()));
        return requireTask(request.getTenantId(), receipt.childTaskId());
    }

    @Override
    public TaskRecord transition(TaskRecord task, TaskStatus target, String reasonCode, String reason,
                                 TaskActorType actorType, String actorId, String correlationId,
                                 String idempotencyKey) {
        if (task.getStatus() == target) {
            return task;
        }
        return taskDomain.transition(new TaskStateTransitionCommand(
                task.getTenantId(), task.getTaskId(), task.getVersion(), target, reasonCode, reason,
                actorType, actorId, correlationId, idempotencyKey, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
