package com.opensocket.aievent.core.enforcement.activation.runtime;

import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.task.TaskRecord;

/** Produces browser-safe Task projections before comparison or response serving. */
@Component
public final class TaskRecordVisibilityProjector {
    public List<TaskRecord> project(List<TaskRecord> source, VisibilityLevel visibility) {
        if (source == null || source.isEmpty()) return List.of();
        return source.stream().map(task -> project(task, visibility)).toList();
    }

    public TaskRecord project(TaskRecord source, VisibilityLevel visibility) {
        if (source == null) return null;
        TaskRecord copy = new TaskRecord();
        BeanUtils.copyProperties(source, copy);
        copy.setCreationIdempotencyKey(null);
        copy.setExternalExecutionKey(null);
        copy.setDispatchRecoveryClaimedBy(null);
        copy.setDispatchRecoveryClaimUntil(null);
        if (visibility == null || visibility == VisibilityLevel.FULL) return copy;
        if (visibility == VisibilityLevel.SENSITIVE || visibility == VisibilityLevel.STANDARD) return copy;
        // SUMMARY/METADATA/NONE may retain coarse provenance dimensions but not credential,
        // network, decision or trace identifiers that could expose security investigation data.
        copy.setOriginCredentialId(null);
        copy.setOriginOauthClientId(null);
        copy.setOriginSourceIp(null);
        copy.setAuthorizationDecisionId(null);
        copy.setRequestId(null);
        copy.setTraceId(null);
        copy.setDescription(null);
        copy.setSourceEventId(null);
        copy.setObjectId(null);
        copy.setErrorCode(null);
        copy.setRequestedSkill(null);
        copy.setCorrelationId(null);
        copy.setRequestingAgentId(null);
        copy.setA2aPolicyId(null);
        copy.setMatchedFlowId(null);
        copy.setMatchedRuleId(null);
        copy.setClassificationResultJson("{}");
        copy.setRoutingPath(null);
        copy.setRoutingPolicy(null);
        copy.setRequiredCapabilities(List.of());
        copy.setCreatedReason(null);
        copy.setDispatchRetryReason(null);
        copy.setLifecycleReason(null);
        copy.setFailureCode(null);
        if (visibility == VisibilityLevel.METADATA || visibility == VisibilityLevel.NONE) {
            copy.setOriginPrincipalId(null);
            copy.setActorPrincipalId(null);
            copy.setOriginGroupId(null);
            copy.setOriginApiResource(null);
            copy.setTitle("Restricted task");
            copy.setIncidentId(null);
            copy.setSiteId(null);
            copy.setPlantId(null);
            copy.setOriginSourceSystem(null);
            copy.setTargetSystem(null);
            copy.setEventType(null);
            copy.setTaskTypeCode(null);
        }
        return copy;
    }
}
