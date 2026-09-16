package com.opensocket.aievent.core.evidence;

import java.time.OffsetDateTime;
import java.util.Map;

/** A0-R8 request to append immutable evidence and, optionally, a separable payload. */
public record ExecutionEvidenceAppendRequest(
        String tenantId,
        String evidenceType,
        String sourceFamily,
        String sourceRef,
        String taskId,
        String planId,
        Integer planRevision,
        String stepId,
        String assignmentId,
        String dispatchIntentId,
        String decisionId,
        String policySnapshotRef,
        String actorType,
        String actorId,
        String classification,
        String contentType,
        Object payload,
        String digestKeyRef,
        String digestKeyVersion,
        String retentionPolicyRef,
        Map<String,Object> details,
        OffsetDateTime occurredAt) {}
