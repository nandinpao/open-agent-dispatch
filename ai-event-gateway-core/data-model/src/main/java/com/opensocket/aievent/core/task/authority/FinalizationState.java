package com.opensocket.aievent.core.task.authority;

/** Durable A0-R2 finalization execution state. */
public enum FinalizationState { NONE, PENDING, RUNNING, RETRY_PENDING, MANUAL_RECOVERY_REQUIRED, COMPLETED }
