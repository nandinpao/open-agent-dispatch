package com.opensocket.aievent.database.persistence.task.converter;


import java.util.List;
import java.util.Locale;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.database.persistence.task.po.TaskPo;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.authority.EvidenceAvailabilityRequirement;
import com.opensocket.aievent.core.task.authority.FinalizationState;
import com.opensocket.aievent.core.task.authority.TaskCancellationState;
import com.opensocket.aievent.core.task.authority.TerminalizationReason;
import com.opensocket.aievent.core.task.v53.TaskLifecycle;
import com.opensocket.aievent.core.task.v53.TaskOutcome;
import com.opensocket.aievent.core.task.v53.TaskPhase;

import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;
import com.opensocket.aievent.core.task.domain.TaskActorType;
import com.opensocket.aievent.core.task.domain.TaskIssueSyncPolicy;
import com.opensocket.aievent.core.task.domain.TaskSeverity;
import com.opensocket.aievent.core.task.lineage.TaskFailureDomain;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MYBATIS")
public class TaskPersistenceConverter {
    private final ObjectMapper objectMapper;

    public TaskPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaskPo toPo(TaskRecord task) {
            TaskPo po = new TaskPo();
            po.setTaskId(task.getTaskId());
            po.setTaskKey(firstNonBlank(task.getTaskKey(), task.getTaskId()));
            po.setTitle(firstNonBlank(task.getTitle(), defaultTitle(task)));
            po.setDescription(task.getDescription());
            po.setSeverity((task.getSeverity() == null ? severityFromPriority(task.getPriority()) : task.getSeverity()).name());
            po.setIncidentId(task.getIncidentId());
            po.setSourceEventId(task.getSourceEventId());
            po.setSourceSystem(task.getSourceSystem());
            po.setEventStage(task.getEventStage());
            po.setOriginSourceSystem(task.getOriginSourceSystem());
            po.setTargetSystem(task.getTargetSystem());
            po.setTaskType(task.getTaskType() == null
                    ? TaskType.INCIDENT_RESPONSE.name()
                    : task.getTaskType().name());
            po.setTaskTypeCode(trimToNull(task.getTaskTypeCode()));
            po.setStatus(task.getStatus() == null ? null : task.getStatus().name());
            po.setPriority(task.getPriority() == null ? null : task.getPriority().name());
            po.setTenantId(task.getTenantId());
            po.setSiteId(task.getSiteId());
            po.setPlantId(task.getPlantId());
            po.setObjectType(task.getObjectType());
            po.setObjectId(task.getObjectId());
            po.setEventType(task.getEventType());
            po.setErrorCode(task.getErrorCode());
            po.setRequestedSkill(task.getRequestedSkill());
            po.setHandoffMode(task.getHandoffMode());
            po.setCorrelationId(task.getCorrelationId());
            po.setRootTaskId(firstNonBlank(task.getRootTaskId(), task.getParentTaskId() == null ? task.getTaskId() : null));
            po.setParentTaskId(task.getParentTaskId());
            po.setSourceTaskId(task.getSourceTaskId());
            po.setRequestingTaskId(task.getRequestingTaskId());
            po.setRequestingAgentId(task.getRequestingAgentId());
            po.setA2aPolicyId(task.getA2aPolicyId());
            po.setHopCount(task.getHopCount());
            po.setResultAggregationPolicy(task.getResultAggregationPolicy());
            po.setChildCancellationPolicy(task.getChildCancellationPolicy());
            po.setChildFailurePolicy(task.getChildFailurePolicy());
            po.setIssueSyncPolicy((task.getIssueSyncPolicy() == null ? TaskIssueSyncPolicy.OPTIONAL : task.getIssueSyncPolicy()).name());
            po.setIssueSyncPolicySource(firstNonBlank(task.getIssueSyncPolicySource(), "SYSTEM_FALLBACK"));
            po.setIssueSyncPolicyInheritanceMode(firstNonBlank(task.getIssueSyncPolicyInheritanceMode(), "NONE"));
            po.setIssueSyncPolicyInheritedFromTaskId(trimToNull(task.getIssueSyncPolicyInheritedFromTaskId()));
            po.setCreatedByType((task.getCreatedByType() == null ? TaskActorType.SYSTEM : task.getCreatedByType()).name());
            po.setCreatedById(firstNonBlank(task.getCreatedById(), "CORE"));
            po.setOriginPrincipalType(task.getOriginPrincipalType()); po.setOriginPrincipalId(task.getOriginPrincipalId());
            po.setActorPrincipalType(task.getActorPrincipalType()); po.setActorPrincipalId(task.getActorPrincipalId());
            po.setOriginCredentialId(task.getOriginCredentialId()); po.setOriginOauthClientId(task.getOriginOauthClientId());
            po.setOriginEventId(task.getOriginEventId()); po.setOriginWorkloadSourceSystem(task.getOriginWorkloadSourceSystem());
            po.setOriginCorrelationId(task.getOriginCorrelationId());
            po.setInitialPriority(task.getInitialPriority() == null ? TaskPriority.MEDIUM.name() : task.getInitialPriority().name());
            po.setInitialSeverity(task.getInitialSeverity() == null ? TaskSeverity.MEDIUM.name() : task.getInitialSeverity().name());
            po.setOriginDepartmentId(task.getOriginDepartmentId()); po.setOriginGroupId(task.getOriginGroupId());
            po.setOriginOrganizationSource(task.getOriginOrganizationSource()); po.setOriginOrganizationStatus(task.getOriginOrganizationStatus());
            po.setOriginSourceIp(task.getOriginSourceIp()); po.setOriginApiResource(task.getOriginApiResource());
            po.setAuthorizationDecisionId(task.getAuthorizationDecisionId()); po.setSecurityGlobalEpoch(task.getSecurityGlobalEpoch());
            po.setSecurityTenantEpoch(task.getSecurityTenantEpoch()); po.setSecurityPrincipalEpoch(task.getSecurityPrincipalEpoch());
            po.setRequestId(task.getRequestId()); po.setTraceId(task.getTraceId()); po.setAuthenticationMethod(task.getAuthenticationMethod()); po.setWorkloadPurpose(task.getWorkloadPurpose()); po.setSynthetic(task.isSynthetic());
            po.setProvenanceCapturedAt(task.getProvenanceCapturedAt());
            po.setVersion(Math.max(1L, task.getVersion()));
            po.setCreationIdempotencyKey(task.getCreationIdempotencyKey());
            po.setOwnerDepartmentId(task.getOwnerDepartmentId());
            po.setOwnerGroupId(task.getOwnerGroupId());
            po.setOriginScopeStatus(task.getOriginScopeStatus());
            po.setOriginScopeSourceVersion(task.getOriginScopeSourceVersion());
            po.setOriginScopeInheritedAt(task.getOriginScopeInheritedAt());
            po.setRequesterDepartmentId(task.getRequesterDepartmentId());
            po.setRequesterGroupId(task.getRequesterGroupId());
            po.setRequesterDomainId(task.getRequesterDomainId());
            po.setExecutorDepartmentId(task.getExecutorDepartmentId());
            po.setExecutorGroupId(task.getExecutorGroupId());
            po.setExecutorDomainId(task.getExecutorDomainId());
            po.setVisibilityPolicy(task.getVisibilityPolicy());
            po.setSensitivityLevel(task.getSensitivityLevel());
            po.setMatchedFlowId(task.getMatchedFlowId());
            po.setMatchedRuleId(task.getMatchedRuleId());
            po.setAssignedPoolId(task.getAssignedPoolId());
            po.setTargetPoolId(task.getTargetPoolId());
            po.setClassificationStatus(firstNonBlank(task.getClassificationStatus(), "CLASSIFIED"));
            po.setClassificationResultJson(firstNonBlank(task.getClassificationResultJson(), "{}"));
            po.setRoutingPath(task.getRoutingPath());
            po.setRoutingPolicy(task.getRoutingPolicy());
            po.setRequiredCapabilitiesJson(toJson(task.getRequiredCapabilities()));
            po.setCreatedReason(task.getCreatedReason());
            po.setOccurrenceCountAtCreation(task.getOccurrenceCountAtCreation());
            po.setCreatedAt(task.getCreatedAt());
            po.setUpdatedAt(task.getUpdatedAt());
            po.setTimeoutAt(task.getTimeoutAt());
            po.setTerminalAt(task.getTerminalAt());
            po.setReassignmentCount(task.getReassignmentCount());
            po.setNextDispatchAttemptAt(task.getNextDispatchAttemptAt());
            po.setDispatchAttemptCount(task.getDispatchAttemptCount());
            po.setDispatchRetryReason(task.getDispatchRetryReason());
            po.setDispatchRecoveryClaimedBy(task.getDispatchRecoveryClaimedBy());
            po.setDispatchRecoveryClaimUntil(task.getDispatchRecoveryClaimUntil());
            po.setLifecycleReason(task.getLifecycleReason());
            po.setFailureDomain((task.getFailureDomain() == null ? TaskFailureDomain.NONE : task.getFailureDomain()).name());
            po.setFailureCode(task.getFailureCode());
            po.setFailureAt(task.getFailureAt());
            po.setExternalExecutionKey(task.getExternalExecutionKey());
            return po;
        }

    public TaskRecord toTask(TaskPo po) {
            TaskRecord task = new TaskRecord();
            task.setTaskId(po.getTaskId());
            task.setTaskKey(po.getTaskKey());
            task.setTitle(po.getTitle());
            task.setDescription(po.getDescription());
            task.setSeverity(po.getSeverity() == null ? TaskSeverity.MEDIUM : TaskSeverity.valueOf(po.getSeverity()));
            task.setIncidentId(po.getIncidentId());
            task.setSourceEventId(po.getSourceEventId());
            task.setSourceSystem(po.getSourceSystem());
            task.setEventStage(po.getEventStage());
            task.setOriginSourceSystem(po.getOriginSourceSystem());
            task.setTargetSystem(po.getTargetSystem());
            String storedTaskType = trimToNull(po.getTaskType());
            TaskType platformTaskType = parsePlatformTaskType(storedTaskType);
            task.setTaskType(platformTaskType);
            task.setTaskTypeCode(firstNonBlank(
                    trimToNull(po.getTaskTypeCode()),
                    isPlatformTaskType(storedTaskType) ? null : storedTaskType));
            task.setStatus(TaskStatus.fromStorageValue(po.getStatus()));
            task.setPriority(po.getPriority() == null ? null : TaskPriority.valueOf(po.getPriority()));
            task.setTenantId(po.getTenantId());
            task.setSiteId(po.getSiteId());
            task.setPlantId(po.getPlantId());
            task.setObjectType(po.getObjectType());
            task.setObjectId(po.getObjectId());
            task.setEventType(po.getEventType());
            task.setErrorCode(po.getErrorCode());
            task.setRequestedSkill(po.getRequestedSkill());
            task.setHandoffMode(po.getHandoffMode());
            task.setCorrelationId(po.getCorrelationId());
            task.setRootTaskId(po.getRootTaskId());
            task.setParentTaskId(po.getParentTaskId());
            task.setSourceTaskId(po.getSourceTaskId());
            task.setRequestingTaskId(po.getRequestingTaskId());
            task.setRequestingAgentId(po.getRequestingAgentId());
            task.setA2aPolicyId(po.getA2aPolicyId());
            task.setHopCount(po.getHopCount());
            task.setResultAggregationPolicy(po.getResultAggregationPolicy());
            task.setChildCancellationPolicy(po.getChildCancellationPolicy());
            task.setChildFailurePolicy(po.getChildFailurePolicy());
            task.setIssueSyncPolicy(po.getIssueSyncPolicy() == null ? TaskIssueSyncPolicy.OPTIONAL : TaskIssueSyncPolicy.valueOf(po.getIssueSyncPolicy()));
            task.setIssueSyncPolicySource(firstNonBlank(po.getIssueSyncPolicySource(), "LEGACY_UNRESOLVED"));
            task.setIssueSyncPolicyInheritanceMode(firstNonBlank(po.getIssueSyncPolicyInheritanceMode(), "NONE"));
            task.setIssueSyncPolicyInheritedFromTaskId(po.getIssueSyncPolicyInheritedFromTaskId());
            task.setCreatedByType(po.getCreatedByType() == null ? TaskActorType.SYSTEM : TaskActorType.valueOf(po.getCreatedByType()));
            task.setCreatedById(po.getCreatedById());
            task.setOriginPrincipalType(po.getOriginPrincipalType()); task.setOriginPrincipalId(po.getOriginPrincipalId());
            task.setActorPrincipalType(po.getActorPrincipalType()); task.setActorPrincipalId(po.getActorPrincipalId());
            task.setOriginCredentialId(po.getOriginCredentialId()); task.setOriginOauthClientId(po.getOriginOauthClientId());
            task.setOriginEventId(po.getOriginEventId()); task.setOriginWorkloadSourceSystem(po.getOriginWorkloadSourceSystem());
            task.setOriginCorrelationId(po.getOriginCorrelationId());
            task.setInitialPriority(po.getInitialPriority() == null ? TaskPriority.MEDIUM : TaskPriority.valueOf(po.getInitialPriority()));
            task.setInitialSeverity(po.getInitialSeverity() == null ? TaskSeverity.MEDIUM : TaskSeverity.valueOf(po.getInitialSeverity()));
            task.setOriginDepartmentId(po.getOriginDepartmentId()); task.setOriginGroupId(po.getOriginGroupId());
            task.setOriginOrganizationSource(po.getOriginOrganizationSource()); task.setOriginOrganizationStatus(po.getOriginOrganizationStatus());
            task.setOriginSourceIp(po.getOriginSourceIp()); task.setOriginApiResource(po.getOriginApiResource());
            task.setAuthorizationDecisionId(po.getAuthorizationDecisionId()); task.setSecurityGlobalEpoch(po.getSecurityGlobalEpoch());
            task.setSecurityTenantEpoch(po.getSecurityTenantEpoch()); task.setSecurityPrincipalEpoch(po.getSecurityPrincipalEpoch());
            task.setRequestId(po.getRequestId()); task.setTraceId(po.getTraceId()); task.setAuthenticationMethod(po.getAuthenticationMethod()); task.setWorkloadPurpose(po.getWorkloadPurpose()); task.setSynthetic(po.isSynthetic());
            task.setProvenanceCapturedAt(po.getProvenanceCapturedAt());
            task.setVersion(po.getVersion());
            task.setCreationIdempotencyKey(po.getCreationIdempotencyKey());
            task.setOwnerDepartmentId(po.getOwnerDepartmentId());
            task.setOwnerGroupId(po.getOwnerGroupId());
            task.setOriginScopeStatus(po.getOriginScopeStatus());
            task.setOriginScopeSourceVersion(po.getOriginScopeSourceVersion());
            task.setOriginScopeInheritedAt(po.getOriginScopeInheritedAt());
            task.setRequesterDepartmentId(po.getRequesterDepartmentId());
            task.setRequesterGroupId(po.getRequesterGroupId());
            task.setRequesterDomainId(po.getRequesterDomainId());
            task.setExecutorDepartmentId(po.getExecutorDepartmentId());
            task.setExecutorGroupId(po.getExecutorGroupId());
            task.setExecutorDomainId(po.getExecutorDomainId());
            task.setVisibilityPolicy(po.getVisibilityPolicy());
            task.setSensitivityLevel(po.getSensitivityLevel());
            task.setMatchedFlowId(po.getMatchedFlowId());
            task.setMatchedRuleId(po.getMatchedRuleId());
            task.setAssignedPoolId(po.getAssignedPoolId());
            task.setTargetPoolId(po.getTargetPoolId());
            task.setClassificationStatus(firstNonBlank(po.getClassificationStatus(), "CLASSIFIED"));
            task.setClassificationResultJson(firstNonBlank(po.getClassificationResultJson(), "{}"));
            task.setRoutingPath(po.getRoutingPath());
            task.setRoutingPolicy(po.getRoutingPolicy());
            task.setRequiredCapabilities(fromJson(po.getRequiredCapabilitiesJson()));
            task.setCreatedReason(po.getCreatedReason());
            task.setOccurrenceCountAtCreation(po.getOccurrenceCountAtCreation());
            task.setCreatedAt(po.getCreatedAt());
            task.setUpdatedAt(po.getUpdatedAt());
            task.setTimeoutAt(po.getTimeoutAt());
            task.setTerminalAt(po.getTerminalAt());
            task.setReassignmentCount(po.getReassignmentCount());
            task.setNextDispatchAttemptAt(po.getNextDispatchAttemptAt());
            task.setDispatchAttemptCount(po.getDispatchAttemptCount());
            task.setDispatchRetryReason(po.getDispatchRetryReason());
            task.setDispatchRecoveryClaimedBy(po.getDispatchRecoveryClaimedBy());
            task.setDispatchRecoveryClaimUntil(po.getDispatchRecoveryClaimUntil());
            task.setLifecycleReason(po.getLifecycleReason());
            task.setFailureDomain(po.getFailureDomain() == null ? TaskFailureDomain.NONE : TaskFailureDomain.valueOf(po.getFailureDomain()));
            task.setFailureCode(po.getFailureCode());
            task.setFailureAt(po.getFailureAt());
            task.setExternalExecutionKey(po.getExternalExecutionKey());
            task.setTaskLifecycle(enumValue(TaskLifecycle.class, po.getTaskLifecycle(), TaskLifecycle.CREATED));
            task.setTaskPhase(enumValue(TaskPhase.class, po.getTaskPhase(), TaskPhase.INTAKE));
            task.setTaskOutcome(enumValue(TaskOutcome.class, po.getTaskOutcome(), TaskOutcome.UNRESOLVED));
            task.setTaskStateAuthorityVersion(po.getTaskStateAuthorityVersion());
            task.setTerminalizationReason(enumValue(TerminalizationReason.class, po.getTerminalizationReason(), null));
            task.setTerminalizationRequestedAt(po.getTerminalizationRequestedAt());
            task.setTerminalizationActorRef(po.getTerminalizationActorRef());
            task.setOutcomeResolverVersion(po.getOutcomeResolverVersion());
            task.setCancellationState(enumValue(TaskCancellationState.class, po.getCancellationState(), TaskCancellationState.NONE));
            task.setFinalizationState(enumValue(FinalizationState.class, po.getFinalizationState(), FinalizationState.NONE));
            task.setFinalizationCheckpoint(po.getFinalizationCheckpoint());
            task.setFinalizationAttemptCount(po.getFinalizationAttemptCount());
            task.setFinalizationNextAttemptAt(po.getFinalizationNextAttemptAt());
            task.setFinalizationClaimedBy(po.getFinalizationClaimedBy());
            task.setFinalizationClaimUntil(po.getFinalizationClaimUntil());
            task.setFinalizationLastErrorCode(po.getFinalizationLastErrorCode());
            task.setFinalizationLastErrorMessage(po.getFinalizationLastErrorMessage());
            task.setFinalizationCompletedAt(po.getFinalizationCompletedAt());
            task.setEvidenceAvailabilityRequirement(enumValue(EvidenceAvailabilityRequirement.class, po.getEvidenceAvailabilityRequirement(), EvidenceAvailabilityRequirement.EVIDENCE_REQUIRED_BEFORE_COMMIT));
            return task;
        }

    private TaskType parsePlatformTaskType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return TaskType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Historical rows may contain a tenant/business task code in tasks.task_type.
            // Preserve that opaque value in taskTypeCode and use the platform lifecycle
            // category only for backward-compatible runtime processing.
            return TaskType.INCIDENT_RESPONSE;
        }
    }

    private boolean isPlatformTaskType(String value) {
        if (value == null) {
            return false;
        }
        try {
            TaskType.valueOf(value.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String defaultTitle(TaskRecord task) {
        String type = firstNonBlank(task.getEffectiveTaskTypeCode(), "Task");
        String id = firstNonBlank(task.getTaskId(), "new");
        return (type + " " + id).length() > 256 ? (type + " " + id).substring(0, 256) : type + " " + id;
    }

    private TaskSeverity severityFromPriority(TaskPriority priority) {
        if (priority == null) return TaskSeverity.MEDIUM;
        return switch (priority) {
            case CRITICAL -> TaskSeverity.CRITICAL;
            case HIGH -> TaskSeverity.HIGH;
            case LOW -> TaskSeverity.LOW;
            default -> TaskSeverity.MEDIUM;
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String toJson(Object value) {
            try { return objectMapper.writeValueAsString(value == null ? List.of() : value); } catch (Exception ex) { return "[]"; }
        }

    public List<String> fromJson(String json) {
            try { if (json == null || json.isBlank()) return List.of(); return objectMapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception ex) { return List.of(); }
        }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        String normalized = trimToNull(value);
        if (normalized == null) return fallback;
        try { return Enum.valueOf(type, normalized.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return fallback; }
    }

}
