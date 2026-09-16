package com.opensocket.aievent.core.task.authority;

/** Cancellation command/outcome state is intentionally separate from TaskLifecycle. */
public enum TaskCancellationState { NONE, REQUESTED, CONFIRMED, REJECTED, UNCONFIRMED }
