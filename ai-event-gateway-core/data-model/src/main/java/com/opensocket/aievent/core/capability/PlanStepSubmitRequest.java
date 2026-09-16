package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Idempotent Step submission request after governance and HOW have been resolved. */
public record PlanStepSubmitRequest(String idempotencyKey,OffsetDateTime deadlineAt) {}
