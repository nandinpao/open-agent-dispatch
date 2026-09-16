package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Stage 10 rollout policy. CONTROLLED_LIVE still grants no provider or execution authority. */
public record SemanticTriageRuntimePolicy(
        String tenantId, String policyId, String rolloutMode, double sampleRate,
        boolean controlledLiveApproved, String modelProfileRef, String promptProfileRef,
        String status, int version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
