package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.a2a.A2AResult;
import com.opensocket.aievent.core.a2a.A2AResultRepository;
import com.opensocket.aievent.core.integration.handoff.HandoffContextRepository;
import com.opensocket.aievent.core.integration.handoff.HandoffContextSnapshot;
import com.opensocket.aievent.core.integration.handoff.ResultContextSnapshot;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Resolves immutable Handoff and Result snapshots through their Domain authority and owning Task. */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = "enabled", havingValue = "true")
public final class HandoffResourceDescriptorResolver implements ResourceDescriptorResolverPort {
    private final HandoffContextRepository handoffs;
    private final TaskRepository tasks;
    private final A2AResultRepository results;

    public HandoffResourceDescriptorResolver(HandoffContextRepository handoffs, TaskRepository tasks,
            A2AResultRepository results) {
        this.handoffs = handoffs;
        this.tasks = tasks;
        this.results = results;
    }

    @Override
    public boolean supports(ResourceType type) {
        return type == ResourceType.TASK_CONTEXT_SNAPSHOT || type == ResourceType.TASK_RESULT;
    }

    @Override
    public Optional<ResourceDescriptor> resolve(ResourceRef ref, DescriptorResolutionContext context) {
        if (ref.resourceType() == ResourceType.TASK_CONTEXT_SNAPSHOT) {
            return handoffs.findSnapshot(ref.tenantId(), ref.resourceId())
                    .flatMap(snapshot -> descriptor(ref, snapshot, context));
        }
        if (ref.resourceType() == ResourceType.TASK_RESULT) {
            Optional<ResourceDescriptor> handoffResult = handoffs.findResultSnapshot(ref.tenantId(), ref.resourceId())
                    .flatMap(snapshot -> descriptor(ref, snapshot, context));
            return handoffResult.isPresent() ? handoffResult
                    : results.findById(ref.tenantId(), ref.resourceId())
                            .flatMap(result -> descriptor(ref, result, context));
        }
        return Optional.empty();
    }

    private Optional<ResourceDescriptor> descriptor(ResourceRef ref, HandoffContextSnapshot snapshot,
            DescriptorResolutionContext context) {
        String taskId = first(snapshot.targetTaskId(), snapshot.sourceTaskId());
        TaskRecord task = taskId == null ? null : tasks.findByTenantAndId(ref.tenantId(), taskId).orElse(null);
        if (task == null) return Optional.empty();
        OwnershipDescriptor ownership = ownership(task);
        ResourceRef parent = new ResourceRef(ref.tenantId(), ResourceType.TASK, task.getTaskId());
        ResourceRef root = new ResourceRef(ref.tenantId(), ResourceType.TASK,
                first(snapshot.rootTaskId(), task.getRootTaskId(), task.getTaskId()));
        SensitivityLevel sensitivity = BridgeDescriptorSupport.sensitivity(
                snapshot.sensitivityLevel() == null ? null : snapshot.sensitivityLevel().name(),
                SensitivityLevel.CONFIDENTIAL);
        VisibilityDescriptor visibility = BridgeDescriptorSupport.visibility(sensitivity, VisibilityLevel.SENSITIVE,
                snapshot.contextPolicyId(), snapshot.policyVersion());
        ResourceSecurityState state = snapshot.status() == null
                ? ResourceSecurityState.RESTRICTED
                : switch (snapshot.status()) {
                    case REJECTED, SUPERSEDED, EXPIRED -> ResourceSecurityState.ARCHIVED;
                    default -> ResourceSecurityState.NORMAL;
                };
        String canonical = String.join("|", snapshot.snapshotId(), snapshot.contentHash(),
                String.valueOf(snapshot.status()), String.valueOf(snapshot.releaseStatus()),
                String.valueOf(snapshot.snapshotVersion()), String.valueOf(snapshot.rowVersion()), ownership.toString());
        return Optional.of(new ResourceDescriptor(ref, snapshot.snapshotId(), ownership, parent, root, visibility,
                state, 0, snapshot.rowVersion(), DescriptorAuthority.A2A_DOMAIN,
                BridgeDescriptorSupport.hash(ref, canonical),
                BridgeDescriptorSupport.instant(snapshot.createdAt(), context.requestedAt())));
    }

    private Optional<ResourceDescriptor> descriptor(ResourceRef ref, ResultContextSnapshot snapshot,
            DescriptorResolutionContext context) {
        String taskId = first(snapshot.targetTaskId(), snapshot.sourceTaskId());
        TaskRecord task = taskId == null ? null : tasks.findByTenantAndId(ref.tenantId(), taskId).orElse(null);
        if (task == null) return Optional.empty();
        OwnershipDescriptor ownership = ownership(task);
        ResourceRef parent = new ResourceRef(ref.tenantId(), ResourceType.TASK, task.getTaskId());
        ResourceRef root = new ResourceRef(ref.tenantId(), ResourceType.TASK,
                first(snapshot.rootTaskId(), task.getRootTaskId(), task.getTaskId()));
        SensitivityLevel sensitivity = BridgeDescriptorSupport.sensitivity(task.getSensitivityLevel(),
                SensitivityLevel.CONFIDENTIAL);
        VisibilityDescriptor visibility = BridgeDescriptorSupport.visibility(sensitivity, VisibilityLevel.SUMMARY,
                snapshot.policyId(), snapshot.snapshotVersion());
        String canonical = String.join("|", snapshot.resultSnapshotId(), snapshot.resultContentHash(),
                String.valueOf(snapshot.status()), String.valueOf(snapshot.snapshotVersion()), ownership.toString());
        return Optional.of(new ResourceDescriptor(ref, snapshot.resultSnapshotId(), ownership, parent, root,
                visibility, ResourceSecurityState.NORMAL, 0, Math.max(1, snapshot.snapshotVersion()),
                DescriptorAuthority.A2A_DOMAIN, BridgeDescriptorSupport.hash(ref, canonical),
                BridgeDescriptorSupport.instant(snapshot.createdAt(), context.requestedAt())));
    }

    private Optional<ResourceDescriptor> descriptor(ResourceRef ref, A2AResult result,
            DescriptorResolutionContext context) {
        String taskId = first(result.getChildTaskId(), result.getParentTaskId());
        TaskRecord task = taskId == null ? null : tasks.findByTenantAndId(ref.tenantId(), taskId).orElse(null);
        if (task == null) return Optional.empty();
        OwnershipDescriptor ownership = ownership(task);
        ResourceRef parent = new ResourceRef(ref.tenantId(), ResourceType.TASK, task.getTaskId());
        ResourceRef root = new ResourceRef(ref.tenantId(), ResourceType.TASK,
                first(result.getRootTaskId(), task.getRootTaskId(), task.getTaskId()));
        SensitivityLevel sensitivity = BridgeDescriptorSupport.sensitivity(task.getSensitivityLevel(),
                SensitivityLevel.CONFIDENTIAL);
        VisibilityDescriptor visibility = BridgeDescriptorSupport.visibility(sensitivity, VisibilityLevel.SUMMARY,
                "A2A_RESULT", result.getPolicyVersion());
        ResourceSecurityState state = result.getResultStatus() == null
                ? ResourceSecurityState.RESTRICTED : ResourceSecurityState.NORMAL;
        String canonical = String.join("|", result.getResultId(), String.valueOf(result.getResultStatus()),
                String.valueOf(result.getResultFingerprint()), String.valueOf(result.getVersion()), ownership.toString());
        return Optional.of(new ResourceDescriptor(ref, result.getResultId(), ownership, parent, root,
                visibility, state, 0, Math.max(1, result.getVersion()), DescriptorAuthority.A2A_DOMAIN,
                BridgeDescriptorSupport.hash(ref, canonical),
                BridgeDescriptorSupport.instant(result.getCreatedAt(), context.requestedAt())));
    }

    private static OwnershipDescriptor ownership(TaskRecord task) {
        return new OwnershipDescriptor(BridgeDescriptorSupport.owner(task.getOwnerDepartmentId()),
                BridgeDescriptorSupport.owner(task.getOwnerGroupId()), "", "",
                BridgeDescriptorSupport.owner(task.getRequesterDepartmentId()),
                BridgeDescriptorSupport.owner(task.getExecutorDepartmentId()), task.getVersion());
    }

    private static String first(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
}
