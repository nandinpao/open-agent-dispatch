package com.opensocket.aievent.core.capability;

/** Retry always clears prior WHO MAY/WHO SHOULD/HOW evidence and returns the Step to READY. */
public record PlanStepRetryRequest(String reason) {}
