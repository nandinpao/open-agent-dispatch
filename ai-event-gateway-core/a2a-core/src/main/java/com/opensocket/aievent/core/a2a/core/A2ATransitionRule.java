package com.opensocket.aievent.core.a2a.core;

import com.opensocket.aievent.core.a2a.A2AOperationalStage;
import com.opensocket.aievent.core.a2a.A2ARequestStatus;
import com.opensocket.aievent.core.a2a.A2ATransitionCommand;
import com.opensocket.aievent.core.a2a.A2ATransitionEvidenceType;
import com.opensocket.aievent.core.a2a.A2ATransitionFailureHandling;
import com.opensocket.aievent.core.a2a.A2ATransitionPermission;
import com.opensocket.aievent.core.a2a.A2ATransitionTimeoutPolicy;

/** A machine-readable transition definition used by the runtime state machine. */
public record A2ATransitionRule(
        String id,
        A2ARequestStatus sourceStatus,
        A2AOperationalStage sourceStage,
        A2ATransitionCommand command,
        A2ARequestStatus targetStatus,
        A2AOperationalStage targetStage,
        A2ATransitionPermission permission,
        String idempotencyScope,
        A2ATransitionEvidenceType evidenceType,
        String defaultAuditReason,
        String domainEventCode,
        A2ATransitionFailureHandling failureHandling,
        A2ATransitionTimeoutPolicy timeoutPolicy,
        boolean recovery) {
}
