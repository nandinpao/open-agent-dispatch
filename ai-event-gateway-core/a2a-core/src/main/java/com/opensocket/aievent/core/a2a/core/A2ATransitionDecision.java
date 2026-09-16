package com.opensocket.aievent.core.a2a.core;

import com.opensocket.aievent.core.a2a.A2ABlockerCode;
import com.opensocket.aievent.core.a2a.A2AOperationalStage;
import com.opensocket.aievent.core.a2a.A2ARequestStatus;
import com.opensocket.aievent.core.a2a.A2ATransitionCommand;
import com.opensocket.aievent.core.a2a.A2ATransitionEvidenceType;
import com.opensocket.aievent.core.a2a.A2ATransitionFailureHandling;
import com.opensocket.aievent.core.a2a.A2ATransitionPermission;
import com.opensocket.aievent.core.a2a.A2ATransitionTimeoutPolicy;

public record A2ATransitionDecision(
        String ruleId,
        A2ARequestStatus fromStatus,
        A2AOperationalStage fromStage,
        A2ARequestStatus toStatus,
        A2AOperationalStage toStage,
        A2ABlockerCode blockerCode,
        A2ATransitionCommand command,
        A2ATransitionPermission requiredPermission,
        String idempotencyScope,
        A2ATransitionEvidenceType evidenceType,
        String evidenceReference,
        long expectedVersion,
        long resultingVersion,
        String auditReason,
        String domainEventCode,
        A2ATransitionFailureHandling failureHandling,
        A2ATransitionTimeoutPolicy timeoutPolicy,
        boolean recovery) {
}
