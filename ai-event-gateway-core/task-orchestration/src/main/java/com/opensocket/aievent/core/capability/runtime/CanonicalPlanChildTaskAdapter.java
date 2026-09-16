package com.opensocket.aievent.core.capability.runtime;

import com.opensocket.aievent.core.capability.CapabilityRequirement;
import com.opensocket.aievent.core.capability.PlanChildTaskPort;
import com.opensocket.aievent.core.capability.PlanChildTaskReference;
import com.opensocket.aievent.core.capability.PlanChildTaskRequest;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;
import com.opensocket.aievent.core.task.domain.TaskActorType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical provider-neutral child Task authority for capability delegation and Stage 9 Plan Steps.
 * It never accepts or copies Agent/Pool/Peer/Domain/endpoint selection into the child Task.
 */
@Component
public class CanonicalPlanChildTaskAdapter implements PlanChildTaskPort {
    private static final Logger log = LoggerFactory.getLogger(CanonicalPlanChildTaskAdapter.class);
    private final TaskRepository tasks;

    public CanonicalPlanChildTaskAdapter(TaskRepository tasks) { this.tasks = tasks; }

    @Override
    @Transactional
    public PlanChildTaskReference createChildTask(PlanChildTaskRequest request) {
        if (request == null) throw new IllegalArgumentException("PlanChildTaskRequest is required");
        String tenant = required(request.tenantId(), "tenantId");
        String runId = required(request.runId(), "runId");
        String stepId = required(request.stepId(), "stepId");
        boolean delegation = runId.startsWith("cap-delegation-");
        boolean planStep = runId.startsWith("plan-run-");
        if (!delegation && !planStep) throw new IllegalArgumentException("CANONICAL_CHILD_TASK_SCOPE_VIOLATION");
        if (request.attemptNo() < 1) throw new IllegalArgumentException("attemptNo must be >= 1");
        if (delegation && (!"delegated-capability".equals(stepId) || request.attemptNo()!=1
                || (request.parentArtifactRefs()!=null && !request.parentArtifactRefs().isEmpty()))) {
            throw new IllegalArgumentException("STAGE4_CHILD_TASK_PORT_SCOPE_VIOLATION");
        }

        String parentId = required(normalizeTaskRef(request.parentTaskRef()), "parentTaskRef");
        CapabilityRequirement requirement = request.requiredCapability();
        if (requirement == null) throw new IllegalArgumentException("requiredCapability is required");
        String capability = required(requirement.capabilityCode(), "requiredCapability.capabilityCode").toLowerCase();
        String idempotencyKey = (delegation?"cap-child:":"plan-step-child:") + runId + ":" + stepId + ":" + request.attemptNo();

        TaskRecord parent = tasks.findByTenantAndId(tenant, parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent Task not found: " + parentId));
        log.info("capability_child_task_runtime_path_selected adapter={} tenantId={} parentTaskId={} runId={} stepId={} delegation={} planStep={} parentIssueSyncPolicy={} parentIssueSyncPolicySource={} parentInheritanceMode={} parentInheritedFromTaskId={} parentMatchedFlowId={} parentMatchedRuleId={} routingContract=CANONICAL_PLAN_CHILD",
                CanonicalPlanChildTaskAdapter.class.getSimpleName(), tenant, parent.getTaskId(), runId, stepId, delegation, planStep,
                parent.getIssueSyncPolicy(), parent.getIssueSyncPolicySource(), parent.getIssueSyncPolicyInheritanceMode(),
                parent.getIssueSyncPolicyInheritedFromTaskId(), parent.getMatchedFlowId(), parent.getMatchedRuleId());
        TaskRecord existing = tasks.findByParentTaskId(tenant, parentId, 1000).stream()
                .filter(task -> idempotencyKey.equals(task.getCreationIdempotencyKey()))
                .findFirst().orElse(null);
        if (existing != null) {
            log.info("capability_child_task_runtime_path_replayed adapter={} parentTaskId={} childTaskId={} childIssueSyncPolicy={} childIssueSyncPolicySource={} childInheritanceMode={} childInheritedFromTaskId={} childMatchedFlowId={} childMatchedRuleId={} creationIdempotencyKey={}",
                    CanonicalPlanChildTaskAdapter.class.getSimpleName(), parent.getTaskId(), existing.getTaskId(), existing.getIssueSyncPolicy(),
                    existing.getIssueSyncPolicySource(), existing.getIssueSyncPolicyInheritanceMode(), existing.getIssueSyncPolicyInheritedFromTaskId(),
                    existing.getMatchedFlowId(), existing.getMatchedRuleId(), idempotencyKey);
            return new PlanChildTaskReference(existing.getTaskId(), true);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord child = new TaskRecord();
        child.setTaskId("task-" + UUID.randomUUID());
        child.setTaskKey(CanonicalChildTaskKey.create(delegation, runId, stepId, request.attemptNo()));
        child.setTitle((delegation?"Capability delegation: ":"Plan step: ") + capability);
        child.setDescription((delegation?"Provider-neutral delegated child Task for capability ":"Provider-neutral Plan Step child Task for capability ") + capability);
        child.setIncidentId(parent.getIncidentId());
        child.setSourceEventId(parent.getSourceEventId());
        child.setSourceSystem(parent.getSourceSystem());
        child.setOriginSourceSystem(first(parent.getOriginSourceSystem(), parent.getSourceSystem()));
        child.setEventStage(delegation?"CAPABILITY_DELEGATION":"PLAN_EXECUTION");
        child.setTargetSystem(null);
        child.setTaskType(TaskType.RESOLUTION);
        child.setTaskTypeCode(delegation?"CAPABILITY_DELEGATION":"PLAN_EXECUTION_STEP");
        child.setRequiredCapabilities(List.of(capability));
        child.setStatus(TaskStatus.QUEUED);
        child.setPriority(parent.getPriority() == null ? TaskPriority.MEDIUM : parent.getPriority());
        child.setTenantId(tenant);
        child.setSiteId(parent.getSiteId());
        child.setPlantId(parent.getPlantId());
        child.setObjectType(parent.getObjectType());
        child.setObjectId(parent.getObjectId());
        child.setEventType(parent.getEventType());
        child.setErrorCode(parent.getErrorCode());
        child.setCorrelationId(first(parent.getCorrelationId(), runId));
        child.setRootTaskId(first(parent.getRootTaskId(), parent.getTaskId()));
        child.setParentTaskId(parent.getTaskId());
        child.setSourceTaskId(parent.getTaskId());
        child.setRequestingTaskId(parent.getTaskId());
        if (delegation) {
            String requestingAgentId = required(request.requestingAgentId(), "requestingAgentId");
            child.setRequestingAgentId(requestingAgentId);
            child.setCreatedByType(TaskActorType.AGENT);
            child.setCreatedById(requestingAgentId);
        } else {
            child.setRequestingAgentId(null);
            child.setCreatedByType(TaskActorType.SYSTEM);
            child.setCreatedById("PLAN_RUNTIME");
        }
        child.setOwnerDepartmentId(parent.getOwnerDepartmentId());
        child.setOwnerGroupId(parent.getOwnerGroupId());
        child.setRequesterDepartmentId(parent.getExecutorDepartmentId());
        child.setRequesterGroupId(parent.getExecutorGroupId());
        child.setRequesterDomainId(parent.getExecutorDomainId());
        child.setExecutorDepartmentId(null);
        child.setExecutorGroupId(null);
        child.setExecutorDomainId(null);
        child.setSensitivityLevel(first(requirement.dataClassification(), parent.getSensitivityLevel()));
        child.setAssignedPoolId(null);
        child.setTargetPoolId(null);
        child.setRoutingPolicy("CAPABILITY_GOVERNED");
        child.setRoutingPath(delegation?"CAPABILITY_AUTHORITY_TO_CANONICAL_DISPATCH":"PLAN_STEP_TO_CANONICAL_EXECUTION");
        // Child Tasks do not re-run Flow/Rule matching. Preserve that routing truth, but inherit the
        // parent's already-resolved Issue policy authority explicitly so TaskRecord defaults can never
        // silently replace REQUIRED/NONE/MANUAL with OPTIONAL on this canonical runtime path.
        child.setIssueSyncPolicy(parent.getIssueSyncPolicy());
        child.setIssueSyncPolicySource(delegation ? "CAPABILITY_PARENT_INHERITED" : "PLAN_PARENT_INHERITED");
        child.setIssueSyncPolicyInheritanceMode(delegation ? "CAPABILITY_PARENT" : "PLAN_PARENT");
        child.setIssueSyncPolicyInheritedFromTaskId(parent.getTaskId());
        child.setCreationIdempotencyKey(idempotencyKey);
        child.setCreatedReason((delegation?"Stage 4 capability delegation":"Stage 9 governed Plan Step") + "; runId=" + runId + "; stepId=" + stepId + "; attempt=" + request.attemptNo());
        child.inheritOriginProvenance(parent, "SYSTEM", planStep?"PLAN_RUNTIME":"CAPABILITY_RUNTIME", now);
        child.setCreatedAt(now);
        child.setUpdatedAt(now);
        child.setVersion(1L);
        log.info("capability_child_task_issue_policy_observed_before_save adapter={} parentTaskId={} childTaskId={} parentIssueSyncPolicy={} parentIssueSyncPolicySource={} childIssueSyncPolicy={} childIssueSyncPolicySource={} childInheritanceMode={} childInheritedFromTaskId={} childMatchedFlowId={} childMatchedRuleId={} policyMutatedByAdapter=true routingPath={}",
                CanonicalPlanChildTaskAdapter.class.getSimpleName(), parent.getTaskId(), child.getTaskId(), parent.getIssueSyncPolicy(),
                parent.getIssueSyncPolicySource(), child.getIssueSyncPolicy(), child.getIssueSyncPolicySource(), child.getIssueSyncPolicyInheritanceMode(),
                child.getIssueSyncPolicyInheritedFromTaskId(), child.getMatchedFlowId(), child.getMatchedRuleId(), child.getRoutingPath());
        TaskRecord saved = tasks.save(child);
        log.info("capability_child_task_issue_policy_persisted adapter={} parentTaskId={} childTaskId={} childIssueSyncPolicy={} childIssueSyncPolicySource={} childInheritanceMode={} childInheritedFromTaskId={} childMatchedFlowId={} childMatchedRuleId={} policyMutatedByAdapter=true routingPath={}",
                CanonicalPlanChildTaskAdapter.class.getSimpleName(), parent.getTaskId(), saved.getTaskId(), saved.getIssueSyncPolicy(),
                saved.getIssueSyncPolicySource(), saved.getIssueSyncPolicyInheritanceMode(), saved.getIssueSyncPolicyInheritedFromTaskId(),
                saved.getMatchedFlowId(), saved.getMatchedRuleId(), saved.getRoutingPath());
        return new PlanChildTaskReference(saved.getTaskId(), true);
    }


    private static String normalizeTaskRef(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("task://")) return v.substring(7);
        if (v.startsWith("task:")) return v.substring(5);
        return v;
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String first(String a, String b) { return a != null && !a.isBlank() ? a : b; }
}
