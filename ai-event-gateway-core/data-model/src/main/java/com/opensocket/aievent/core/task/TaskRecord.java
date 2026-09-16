package com.opensocket.aievent.core.task;

import com.opensocket.aievent.core.task.authority.EvidenceAvailabilityRequirement;
import com.opensocket.aievent.core.task.authority.FinalizationState;
import com.opensocket.aievent.core.task.authority.TaskCancellationState;
import com.opensocket.aievent.core.task.authority.TerminalizationReason;
import com.opensocket.aievent.core.task.v53.TaskLifecycle;
import com.opensocket.aievent.core.task.v53.TaskOutcome;
import com.opensocket.aievent.core.task.v53.TaskPhase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import com.opensocket.aievent.core.organization.TaskVisibilityPolicy;
import com.opensocket.aievent.core.task.domain.TaskActorType;
import com.opensocket.aievent.core.task.domain.TaskIssueSyncPolicy;
import com.opensocket.aievent.core.task.domain.TaskSeverity;
import com.opensocket.aievent.core.task.lineage.TaskFailureDomain;

public class TaskRecord {
    private String taskId;
    private String taskKey;
    private String title;
    private String description;
    private TaskSeverity severity = TaskSeverity.MEDIUM;
    private String incidentId;
    private String sourceEventId;
    private String sourceSystem;
    private String eventStage;
    private String originSourceSystem;
    private String targetSystem;
    private TaskType taskType;
    /**
     * Business task contract code resolved from sourceSystem/objectType/eventType/errorCode.
     * Kept separate from taskType enum so tenant-defined business task codes
     * can route through the strict dispatch contract while legacy lifecycle code remains enum-compatible.
     */
    private String taskTypeCode;
    private TaskStatus status;
    private TaskPriority priority;
    private String tenantId;
    private String siteId;
    private String plantId;
    private String objectType;
    private String objectId;
    private String eventType;
    private String errorCode;
    private String requestedSkill;
    private String handoffMode;
    private String correlationId;
    private String rootTaskId;
    private String parentTaskId;
    private String sourceTaskId;
    private String requestingTaskId;
    private String requestingAgentId;
    private String a2aPolicyId;
    private int hopCount;
    private String resultAggregationPolicy = "MANUAL_REVIEW";
    private String childCancellationPolicy = "MANUAL_DECISION";
    private String childFailurePolicy = "WAIT_HUMAN";
    private TaskIssueSyncPolicy issueSyncPolicy = TaskIssueSyncPolicy.OPTIONAL;
    /** Effective Issue policy authority for this Task. Never infer this from matchedFlowId/matchedRuleId. */
    private String issueSyncPolicySource = "SYSTEM_FALLBACK";
    /** A2A inheritance is explicit because A2A child routing does not execute the Flow/Rule matcher. */
    private String issueSyncPolicyInheritanceMode = "NONE";
    private String issueSyncPolicyInheritedFromTaskId;
    private TaskActorType createdByType = TaskActorType.SYSTEM;
    private String createdById = "CORE";
    /** Phase 12.3 immutable workload provenance. Current authorization must never be inferred from these fields. */
    private String originPrincipalType = "SYSTEM";
    private String originPrincipalId = "CORE";
    private String actorPrincipalType = "SYSTEM";
    private String actorPrincipalId = "CORE";
    private String originCredentialId;
    private String originOauthClientId;
    private String originEventId;
    private String originWorkloadSourceSystem;
    private String originCorrelationId;
    private TaskPriority initialPriority = TaskPriority.MEDIUM;
    private TaskSeverity initialSeverity = TaskSeverity.MEDIUM;
    private String originDepartmentId = "UNASSIGNED";
    private String originGroupId;
    private String originOrganizationSource = "UNRESOLVED";
    private String originOrganizationStatus = "UNRESOLVED";
    private String originSourceIp;
    private String originApiResource;
    private String authorizationDecisionId;
    private long securityGlobalEpoch;
    private long securityTenantEpoch;
    private long securityPrincipalEpoch;
    private String requestId;
    private String traceId;
    private String authenticationMethod = "UNKNOWN";
    private String workloadPurpose = "PRODUCTION";
    private boolean synthetic;
    private OffsetDateTime provenanceCapturedAt;
    private long version = 1L;
    private String creationIdempotencyKey;
    private String ownerDepartmentId = "UNASSIGNED";
    private String ownerGroupId;
    /** RS4 immutable origin-scope provenance inherited from Event/Incident. */
    private String originScopeStatus = "UNRESOLVED";
    private Long originScopeSourceVersion;
    private OffsetDateTime originScopeInheritedAt;
    private String requesterDepartmentId = "UNASSIGNED";
    private String requesterGroupId;
    private String requesterDomainId = "UNASSIGNED";
    private String executorDepartmentId = "UNASSIGNED";
    private String executorGroupId;
    private String executorDomainId = "UNASSIGNED";
    private String visibilityPolicy = "TENANT";
    private String sensitivityLevel = "INTERNAL";
    private String matchedFlowId;
    private String matchedRuleId;
    private String assignedPoolId;
    private String targetPoolId;
    private String classificationStatus = "CLASSIFIED";
    private String classificationResultJson = "{}";
    private String routingPath;
    private String routingPolicy;
    private List<String> requiredCapabilities = List.of();
    private String createdReason;
    private long occurrenceCountAtCreation;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime timeoutAt;
    private OffsetDateTime terminalAt;
    private int reassignmentCount;
    private OffsetDateTime nextDispatchAttemptAt;
    private int dispatchAttemptCount;
    private String dispatchRetryReason;
    private String dispatchRecoveryClaimedBy;
    private OffsetDateTime dispatchRecoveryClaimUntil;
    private String lifecycleReason;
    /** Phase 12.4 coarse failure attribution for investigation. Never used as authorization authority. */
    private TaskFailureDomain failureDomain = TaskFailureDomain.NONE;
    private String failureCode;
    private OffsetDateTime failureAt;
    /** Stable key that an external executor must use to deduplicate effectful execution. */
    private String externalExecutionKey;
    /** A0-R2 canonical state authority; legacy TaskStatus is compatibility-only for closure. */
    private TaskLifecycle taskLifecycle = TaskLifecycle.CREATED;
    private TaskPhase taskPhase = TaskPhase.INTAKE;
    private TaskOutcome taskOutcome = TaskOutcome.UNRESOLVED;
    private String taskStateAuthorityVersion;
    private TerminalizationReason terminalizationReason;
    private OffsetDateTime terminalizationRequestedAt;
    private String terminalizationActorRef;
    private String outcomeResolverVersion;
    private TaskCancellationState cancellationState = TaskCancellationState.NONE;
    private FinalizationState finalizationState = FinalizationState.NONE;
    private String finalizationCheckpoint = "NOT_STARTED";
    private int finalizationAttemptCount;
    private OffsetDateTime finalizationNextAttemptAt;
    private String finalizationClaimedBy;
    private OffsetDateTime finalizationClaimUntil;
    private String finalizationLastErrorCode;
    private String finalizationLastErrorMessage;
    private OffsetDateTime finalizationCompletedAt;
    private EvidenceAvailabilityRequirement evidenceAvailabilityRequirement = EvidenceAvailabilityRequirement.EVIDENCE_REQUIRED_BEFORE_COMMIT;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = optionalValue(taskKey); }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = optionalValue(title); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TaskSeverity getSeverity() { return severity; }
    public void setSeverity(TaskSeverity severity) { this.severity = severity == null ? TaskSeverity.MEDIUM : severity; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getOriginSourceSystem() { return originSourceSystem; }
    public void setOriginSourceSystem(String originSourceSystem) { this.originSourceSystem = originSourceSystem; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }
    public String getTaskTypeCode() { return taskTypeCode; }
    public void setTaskTypeCode(String taskTypeCode) { this.taskTypeCode = taskTypeCode; }
    public String getEffectiveTaskTypeCode() {
        if (taskTypeCode != null && !taskTypeCode.isBlank()) {
            return taskTypeCode;
        }
        return taskType == null ? null : taskType.name();
    }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getHandoffMode() { return handoffMode; }
    public void setHandoffMode(String handoffMode) { this.handoffMode = handoffMode; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getRootTaskId() { return rootTaskId; }
    public void setRootTaskId(String rootTaskId) { this.rootTaskId = optionalValue(rootTaskId); }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = optionalValue(parentTaskId); }
    public String getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(String sourceTaskId) { this.sourceTaskId = optionalValue(sourceTaskId); }
    public String getRequestingTaskId() { return requestingTaskId; }
    public void setRequestingTaskId(String requestingTaskId) { this.requestingTaskId = optionalValue(requestingTaskId); }
    public String getRequestingAgentId() { return requestingAgentId; }
    public void setRequestingAgentId(String value) { requestingAgentId = optionalValue(value); }
    public String getA2aPolicyId() { return a2aPolicyId; }
    public void setA2aPolicyId(String value) { a2aPolicyId = optionalValue(value); }
    public int getHopCount() { return hopCount; }
    public void setHopCount(int value) { hopCount = Math.max(0, value); }
    public String getResultAggregationPolicy() { return resultAggregationPolicy; }
    public void setResultAggregationPolicy(String value) { resultAggregationPolicy = defaultValue(value, "MANUAL_REVIEW"); }
    public String getChildCancellationPolicy() { return childCancellationPolicy; }
    public void setChildCancellationPolicy(String value) { childCancellationPolicy = defaultValue(value, "MANUAL_DECISION"); }
    public String getChildFailurePolicy() { return childFailurePolicy; }
    public void setChildFailurePolicy(String value) { childFailurePolicy = defaultValue(value, "WAIT_HUMAN"); }
    public TaskIssueSyncPolicy getIssueSyncPolicy() { return issueSyncPolicy; }
    public void setIssueSyncPolicy(TaskIssueSyncPolicy issueSyncPolicy) { this.issueSyncPolicy = issueSyncPolicy == null ? TaskIssueSyncPolicy.OPTIONAL : issueSyncPolicy; }
    public String getIssueSyncPolicySource() { return issueSyncPolicySource; }
    public void setIssueSyncPolicySource(String value) { issueSyncPolicySource = defaultValue(value, "SYSTEM_FALLBACK"); }
    public String getIssueSyncPolicyInheritanceMode() { return issueSyncPolicyInheritanceMode; }
    public void setIssueSyncPolicyInheritanceMode(String value) { issueSyncPolicyInheritanceMode = defaultValue(value, "NONE"); }
    public String getIssueSyncPolicyInheritedFromTaskId() { return issueSyncPolicyInheritedFromTaskId; }
    public void setIssueSyncPolicyInheritedFromTaskId(String value) { issueSyncPolicyInheritedFromTaskId = optionalValue(value); }
    public TaskActorType getCreatedByType() { return createdByType; }
    public void setCreatedByType(TaskActorType createdByType) { this.createdByType = createdByType == null ? TaskActorType.SYSTEM : createdByType; }
    public String getCreatedById() { return createdById; }
    public void setCreatedById(String createdById) { this.createdById = defaultValue(createdById, "CORE"); }
    public String getOriginPrincipalType() { return originPrincipalType; }
    public void setOriginPrincipalType(String value) { originPrincipalType = defaultValue(value, "SYSTEM"); }
    public String getOriginPrincipalId() { return originPrincipalId; }
    public void setOriginPrincipalId(String value) { originPrincipalId = defaultValue(value, "CORE"); }
    public String getActorPrincipalType() { return actorPrincipalType; }
    public void setActorPrincipalType(String value) { actorPrincipalType = defaultValue(value, "SYSTEM"); }
    public String getActorPrincipalId() { return actorPrincipalId; }
    public void setActorPrincipalId(String value) { actorPrincipalId = defaultValue(value, "CORE"); }
    public String getOriginCredentialId() { return originCredentialId; }
    public void setOriginCredentialId(String value) { originCredentialId = optionalValue(value); }
    public String getOriginOauthClientId() { return originOauthClientId; }
    public void setOriginOauthClientId(String value) { originOauthClientId = optionalValue(value); }
    public String getOriginEventId() { return originEventId; }
    public void setOriginEventId(String value) { originEventId = optionalValue(value); }
    public String getOriginWorkloadSourceSystem() { return originWorkloadSourceSystem; }
    public void setOriginWorkloadSourceSystem(String value) { originWorkloadSourceSystem = optionalValue(value); }
    public String getOriginCorrelationId() { return originCorrelationId; }
    public void setOriginCorrelationId(String value) { originCorrelationId = optionalValue(value); }
    public TaskPriority getInitialPriority() { return initialPriority; }
    public void setInitialPriority(TaskPriority value) { initialPriority = value == null ? TaskPriority.MEDIUM : value; }
    public TaskSeverity getInitialSeverity() { return initialSeverity; }
    public void setInitialSeverity(TaskSeverity value) { initialSeverity = value == null ? TaskSeverity.MEDIUM : value; }
    public String getOriginDepartmentId() { return originDepartmentId; }
    public void setOriginDepartmentId(String value) { originDepartmentId = defaultValue(value, "UNASSIGNED"); }
    public String getOriginGroupId() { return originGroupId; }
    public void setOriginGroupId(String value) { originGroupId = optionalValue(value); }
    public String getOriginOrganizationSource() { return originOrganizationSource; }
    public void setOriginOrganizationSource(String value) { originOrganizationSource = defaultValue(value, "UNRESOLVED"); }
    public String getOriginOrganizationStatus() { return originOrganizationStatus; }
    public void setOriginOrganizationStatus(String value) { originOrganizationStatus = defaultValue(value, "UNRESOLVED"); }
    public String getOriginSourceIp() { return originSourceIp; }
    public void setOriginSourceIp(String value) { originSourceIp = optionalValue(value); }
    public String getOriginApiResource() { return originApiResource; }
    public void setOriginApiResource(String value) { originApiResource = optionalValue(value); }
    public String getAuthorizationDecisionId() { return authorizationDecisionId; }
    public void setAuthorizationDecisionId(String value) { authorizationDecisionId = optionalValue(value); }
    public long getSecurityGlobalEpoch() { return securityGlobalEpoch; }
    public void setSecurityGlobalEpoch(long value) { securityGlobalEpoch = Math.max(0L,value); }
    public long getSecurityTenantEpoch() { return securityTenantEpoch; }
    public void setSecurityTenantEpoch(long value) { securityTenantEpoch = Math.max(0L,value); }
    public long getSecurityPrincipalEpoch() { return securityPrincipalEpoch; }
    public void setSecurityPrincipalEpoch(long value) { securityPrincipalEpoch = Math.max(0L,value); }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = optionalValue(value); }
    public String getTraceId() { return traceId; }
    public void setTraceId(String value) { traceId = optionalValue(value); }
    public String getAuthenticationMethod() { return authenticationMethod; }
    public void setAuthenticationMethod(String value) { authenticationMethod = defaultValue(value, "UNKNOWN"); }
    public String getWorkloadPurpose() { return workloadPurpose; }
    public void setWorkloadPurpose(String value) { workloadPurpose = defaultValue(value, "PRODUCTION"); }
    public boolean isSynthetic() { return synthetic; }
    public void setSynthetic(boolean value) { synthetic = value; }
    public OffsetDateTime getProvenanceCapturedAt() { return provenanceCapturedAt; }
    public void setProvenanceCapturedAt(OffsetDateTime value) { provenanceCapturedAt = value; }

    public void applyCreationProvenance(com.opensocket.aievent.core.workload.WorkloadContext context) {
        if (context == null) return;
        setOriginPrincipalType(context.originPrincipalType()); setOriginPrincipalId(context.originPrincipalId());
        setActorPrincipalType(context.actorPrincipalType()); setActorPrincipalId(context.actorPrincipalId());
        setOriginCredentialId(context.credentialId()); setOriginOauthClientId(context.oauthClientId());
        setOriginEventId(getSourceEventId()); setOriginWorkloadSourceSystem(context.sourceSystem());
        setOriginCorrelationId(context.correlationId()); setInitialPriority(getPriority()); setInitialSeverity(getSeverity());
        setOriginDepartmentId(context.departmentId()); setOriginGroupId(context.groupId());
        setOriginOrganizationSource(context.organizationSource()); setOriginOrganizationStatus(context.organizationStatus());
        setOriginSourceIp(context.clientIp()); setOriginApiResource(context.apiResource());
        setAuthorizationDecisionId(context.authorizationDecisionId());
        setSecurityGlobalEpoch(context.globalSecurityEpoch()); setSecurityTenantEpoch(context.tenantSecurityEpoch());
        setSecurityPrincipalEpoch(context.principalSecurityEpoch()); setRequestId(context.requestId()); setTraceId(context.traceId());
        setAuthenticationMethod(context.authenticationMethod()); setWorkloadPurpose(context.workloadPurpose()); setSynthetic(context.synthetic());
        setProvenanceCapturedAt(java.time.OffsetDateTime.ofInstant(context.capturedAt(), java.time.ZoneOffset.UTC));
        if (getCorrelationId() == null || getCorrelationId().isBlank()) setCorrelationId(context.correlationId());
        setCreatedByType(actorType(context.actorPrincipalType())); setCreatedById(context.actorPrincipalId());
    }

    /**
     * Ensures every persisted Task has immutable origin evidence even when it was created by a
     * non-HTTP/domain path that predates WorkloadContext. This fallback never invents a
     * credential or OAuth client; it derives only what the Task aggregate already knows.
     */
    public void ensureCreationProvenanceDefaults() {
        if (provenanceCapturedAt != null) return;
        String principalType = switch (createdByType == null ? TaskActorType.SYSTEM : createdByType) {
            case USER -> "USER";
            case AGENT -> "AGENT";
            case INTEGRATION -> "INTEGRATION";
            default -> "SYSTEM";
        };
        setOriginPrincipalType(principalType);
        setOriginPrincipalId(createdById);
        setActorPrincipalType(principalType);
        setActorPrincipalId(createdById);
        setOriginEventId(sourceEventId);
        setOriginWorkloadSourceSystem(firstNonBlankLocal(originSourceSystem, sourceSystem));
        setOriginCorrelationId(correlationId);
        setInitialPriority(priority);
        setInitialSeverity(severity);
        setOriginDepartmentId(ownerDepartmentId);
        setOriginGroupId(ownerGroupId);
        setOriginOrganizationSource("TASK_OWNER_SCOPE");
        setOriginOrganizationStatus(originScopeStatus);
        if (getRequestId() == null) setRequestId(correlationId);
        setAuthenticationMethod("DOMAIN");
        OffsetDateTime captured = createdAt != null ? createdAt : (updatedAt != null ? updatedAt : OffsetDateTime.now(java.time.ZoneOffset.UTC));
        setProvenanceCapturedAt(captured);
    }

    public void inheritOriginProvenance(TaskRecord source, String actorType, String actorId, OffsetDateTime capturedAt) {
        if (source == null) return;
        setOriginPrincipalType(source.getOriginPrincipalType()); setOriginPrincipalId(source.getOriginPrincipalId());
        setOriginCredentialId(source.getOriginCredentialId()); setOriginOauthClientId(source.getOriginOauthClientId());
        setOriginEventId(source.getOriginEventId()); setOriginWorkloadSourceSystem(source.getOriginWorkloadSourceSystem());
        setOriginCorrelationId(source.getOriginCorrelationId()); setInitialPriority(getPriority()); setInitialSeverity(getSeverity());
        setOriginDepartmentId(source.getOriginDepartmentId()); setOriginGroupId(source.getOriginGroupId());
        setOriginOrganizationSource(source.getOriginOrganizationSource()); setOriginOrganizationStatus(source.getOriginOrganizationStatus());
        setOriginSourceIp(source.getOriginSourceIp()); setOriginApiResource(source.getOriginApiResource());
        setAuthorizationDecisionId(source.getAuthorizationDecisionId());
        setSecurityGlobalEpoch(source.getSecurityGlobalEpoch()); setSecurityTenantEpoch(source.getSecurityTenantEpoch());
        setSecurityPrincipalEpoch(source.getSecurityPrincipalEpoch()); setRequestId(source.getRequestId()); setTraceId(source.getTraceId());
        setAuthenticationMethod(source.getAuthenticationMethod()); setWorkloadPurpose(source.getWorkloadPurpose()); setSynthetic(source.isSynthetic()); setActorPrincipalType(actorType); setActorPrincipalId(actorId);
        setProvenanceCapturedAt(capturedAt); setCreatedByType(actorType(actorType)); setCreatedById(actorId);
    }

    private TaskActorType actorType(String value) {
        if (value == null) return TaskActorType.SYSTEM;
        return switch (value.trim().toUpperCase()) {
            case "USER" -> TaskActorType.USER;
            case "AGENT", "A2A_AGENT" -> TaskActorType.AGENT;
            case "SERVICE_ACCOUNT", "INTEGRATION" -> TaskActorType.INTEGRATION;
            default -> TaskActorType.SYSTEM;
        };
    }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = Math.max(1L, version); }
    public String getCreationIdempotencyKey() { return creationIdempotencyKey; }
    public void setCreationIdempotencyKey(String creationIdempotencyKey) { this.creationIdempotencyKey = optionalValue(creationIdempotencyKey); }
    public String getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(String value) { ownerDepartmentId = defaultValue(value, "UNASSIGNED"); }
    public String getOwnerGroupId() { return ownerGroupId; }
    public void setOwnerGroupId(String value) { ownerGroupId = optionalValue(value); }
    public String getOriginScopeStatus() { return originScopeStatus; }
    public void setOriginScopeStatus(String value) { originScopeStatus = defaultValue(value, "UNRESOLVED"); }
    public Long getOriginScopeSourceVersion() { return originScopeSourceVersion; }
    public void setOriginScopeSourceVersion(Long value) { originScopeSourceVersion = value; }
    public OffsetDateTime getOriginScopeInheritedAt() { return originScopeInheritedAt; }
    public void setOriginScopeInheritedAt(OffsetDateTime value) { originScopeInheritedAt = value; }
    public String getRequesterDepartmentId() { return requesterDepartmentId; }
    public void setRequesterDepartmentId(String value) { requesterDepartmentId = defaultValue(value, "UNASSIGNED"); }
    public String getRequesterGroupId() { return requesterGroupId; }
    public void setRequesterGroupId(String value) { requesterGroupId = optionalValue(value); }
    public String getRequesterDomainId() { return requesterDomainId; }
    public void setRequesterDomainId(String value) { requesterDomainId = defaultValue(value, "UNASSIGNED"); }
    public String getExecutorDepartmentId() { return executorDepartmentId; }
    public void setExecutorDepartmentId(String value) { executorDepartmentId = defaultValue(value, "UNASSIGNED"); }
    public String getExecutorGroupId() { return executorGroupId; }
    public void setExecutorGroupId(String value) { executorGroupId = optionalValue(value); }
    public String getExecutorDomainId() { return executorDomainId; }
    public void setExecutorDomainId(String value) { executorDomainId = defaultValue(value, "UNASSIGNED"); }
    public String getVisibilityPolicy() { return visibilityPolicy; }
    public void setVisibilityPolicy(String value) {
        String normalized = defaultValue(value, TaskVisibilityPolicy.TENANT.name()).toUpperCase(Locale.ROOT);
        try {
            visibilityPolicy = TaskVisibilityPolicy.valueOf(normalized).name();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("TASK_VISIBILITY_POLICY_INVALID: " + normalized, invalid);
        }
    }
    public String getSensitivityLevel() { return sensitivityLevel; }
    public void setSensitivityLevel(String value) { sensitivityLevel = defaultValue(value, "INTERNAL"); }
    public String getMatchedFlowId() { return matchedFlowId; }
    public void setMatchedFlowId(String matchedFlowId) { this.matchedFlowId = matchedFlowId; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public String getAssignedPoolId() { return assignedPoolId; }
    public void setAssignedPoolId(String assignedPoolId) { this.assignedPoolId = assignedPoolId; }
    public String getTargetPoolId() { return targetPoolId; }
    public void setTargetPoolId(String targetPoolId) { this.targetPoolId = targetPoolId; }
    public String getClassificationStatus() { return classificationStatus; }
    public void setClassificationStatus(String classificationStatus) { this.classificationStatus = classificationStatus == null || classificationStatus.isBlank() ? "CLASSIFIED" : classificationStatus; }
    public String getClassificationResultJson() { return classificationResultJson; }
    public void setClassificationResultJson(String classificationResultJson) { this.classificationResultJson = classificationResultJson == null || classificationResultJson.isBlank() ? "{}" : classificationResultJson; }
    public String getRoutingPath() { return routingPath; }
    public void setRoutingPath(String routingPath) { this.routingPath = routingPath; }
    public String getRoutingPolicy() { return routingPolicy; }
    public void setRoutingPolicy(String routingPolicy) { this.routingPolicy = routingPolicy; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities); }
    public String getCreatedReason() { return createdReason; }
    public void setCreatedReason(String createdReason) { this.createdReason = createdReason; }
    public long getOccurrenceCountAtCreation() { return occurrenceCountAtCreation; }
    public void setOccurrenceCountAtCreation(long occurrenceCountAtCreation) { this.occurrenceCountAtCreation = occurrenceCountAtCreation; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getTimeoutAt() { return timeoutAt; }
    public void setTimeoutAt(OffsetDateTime timeoutAt) { this.timeoutAt = timeoutAt; }
    public OffsetDateTime getTerminalAt() { return terminalAt; }
    public void setTerminalAt(OffsetDateTime terminalAt) { this.terminalAt = terminalAt; }
    public int getReassignmentCount() { return reassignmentCount; }
    public void setReassignmentCount(int reassignmentCount) { this.reassignmentCount = Math.max(0, reassignmentCount); }
    public OffsetDateTime getNextDispatchAttemptAt() { return nextDispatchAttemptAt; }
    public void setNextDispatchAttemptAt(OffsetDateTime nextDispatchAttemptAt) { this.nextDispatchAttemptAt = nextDispatchAttemptAt; }
    public int getDispatchAttemptCount() { return dispatchAttemptCount; }
    public void setDispatchAttemptCount(int dispatchAttemptCount) { this.dispatchAttemptCount = Math.max(0, dispatchAttemptCount); }
    public String getDispatchRetryReason() { return dispatchRetryReason; }
    public void setDispatchRetryReason(String dispatchRetryReason) { this.dispatchRetryReason = dispatchRetryReason; }
    public String getDispatchRecoveryClaimedBy() { return dispatchRecoveryClaimedBy; }
    public void setDispatchRecoveryClaimedBy(String dispatchRecoveryClaimedBy) { this.dispatchRecoveryClaimedBy = dispatchRecoveryClaimedBy; }
    public OffsetDateTime getDispatchRecoveryClaimUntil() { return dispatchRecoveryClaimUntil; }
    public void setDispatchRecoveryClaimUntil(OffsetDateTime dispatchRecoveryClaimUntil) { this.dispatchRecoveryClaimUntil = dispatchRecoveryClaimUntil; }
    public String getLifecycleReason() { return lifecycleReason; }
    public void setLifecycleReason(String lifecycleReason) { this.lifecycleReason = lifecycleReason; }
    public TaskFailureDomain getFailureDomain() { return failureDomain; }
    public void setFailureDomain(TaskFailureDomain failureDomain) { this.failureDomain = failureDomain == null ? TaskFailureDomain.NONE : failureDomain; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = optionalValue(failureCode); }
    public OffsetDateTime getFailureAt() { return failureAt; }
    public void setFailureAt(OffsetDateTime failureAt) { this.failureAt = failureAt; }
    public String getExternalExecutionKey() { return externalExecutionKey; }
    public void setExternalExecutionKey(String externalExecutionKey) { this.externalExecutionKey = externalExecutionKey; }
    public TaskLifecycle getTaskLifecycle() { return taskLifecycle; }
    public void setTaskLifecycle(TaskLifecycle value) { taskLifecycle = value == null ? TaskLifecycle.CREATED : value; }
    public TaskPhase getTaskPhase() { return taskPhase; }
    public void setTaskPhase(TaskPhase value) { taskPhase = value == null ? TaskPhase.INTAKE : value; }
    public TaskOutcome getTaskOutcome() { return taskOutcome; }
    public void setTaskOutcome(TaskOutcome value) { taskOutcome = value == null ? TaskOutcome.UNRESOLVED : value; }
    public String getTaskStateAuthorityVersion() { return taskStateAuthorityVersion; }
    public void setTaskStateAuthorityVersion(String value) { taskStateAuthorityVersion = optionalValue(value); }
    public TerminalizationReason getTerminalizationReason() { return terminalizationReason; }
    public void setTerminalizationReason(TerminalizationReason value) { terminalizationReason = value; }
    public OffsetDateTime getTerminalizationRequestedAt() { return terminalizationRequestedAt; }
    public void setTerminalizationRequestedAt(OffsetDateTime value) { terminalizationRequestedAt = value; }
    public String getTerminalizationActorRef() { return terminalizationActorRef; }
    public void setTerminalizationActorRef(String value) { terminalizationActorRef = optionalValue(value); }
    public String getOutcomeResolverVersion() { return outcomeResolverVersion; }
    public void setOutcomeResolverVersion(String value) { outcomeResolverVersion = optionalValue(value); }
    public TaskCancellationState getCancellationState() { return cancellationState; }
    public void setCancellationState(TaskCancellationState value) { cancellationState = value == null ? TaskCancellationState.NONE : value; }
    public FinalizationState getFinalizationState() { return finalizationState; }
    public void setFinalizationState(FinalizationState value) { finalizationState = value == null ? FinalizationState.NONE : value; }
    public String getFinalizationCheckpoint() { return finalizationCheckpoint; }
    public void setFinalizationCheckpoint(String value) { finalizationCheckpoint = defaultValue(value, "NOT_STARTED"); }
    public int getFinalizationAttemptCount() { return finalizationAttemptCount; }
    public void setFinalizationAttemptCount(int value) { finalizationAttemptCount = Math.max(0, value); }
    public OffsetDateTime getFinalizationNextAttemptAt() { return finalizationNextAttemptAt; }
    public void setFinalizationNextAttemptAt(OffsetDateTime value) { finalizationNextAttemptAt = value; }
    public String getFinalizationClaimedBy() { return finalizationClaimedBy; }
    public void setFinalizationClaimedBy(String value) { finalizationClaimedBy = optionalValue(value); }
    public OffsetDateTime getFinalizationClaimUntil() { return finalizationClaimUntil; }
    public void setFinalizationClaimUntil(OffsetDateTime value) { finalizationClaimUntil = value; }
    public String getFinalizationLastErrorCode() { return finalizationLastErrorCode; }
    public void setFinalizationLastErrorCode(String value) { finalizationLastErrorCode = optionalValue(value); }
    public String getFinalizationLastErrorMessage() { return finalizationLastErrorMessage; }
    public void setFinalizationLastErrorMessage(String value) { finalizationLastErrorMessage = value; }
    public OffsetDateTime getFinalizationCompletedAt() { return finalizationCompletedAt; }
    public void setFinalizationCompletedAt(OffsetDateTime value) { finalizationCompletedAt = value; }
    public EvidenceAvailabilityRequirement getEvidenceAvailabilityRequirement() { return evidenceAvailabilityRequirement; }
    public void setEvidenceAvailabilityRequirement(EvidenceAvailabilityRequirement value) { evidenceAvailabilityRequirement = value == null ? EvidenceAvailabilityRequirement.EVIDENCE_REQUIRED_BEFORE_COMMIT : value; }
    private String firstNonBlankLocal(String... values) {
        if (values != null) for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
    private String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String optionalValue(String value) { return value == null || value.isBlank() ? null : value.trim(); }

}
