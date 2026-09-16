package com.opensocket.aievent.core.task.v53;

/**
 * A0-R2 canonical Task lifecycle authority.
 *
 * <p>{@code TaskStatus} remains a compatibility/runtime signal for legacy workers, but it no
 * longer has canonical close authority. A terminal compatibility signal enters FINALIZING;
 * only the FinalizationCoordinator may commit CLOSED.</p>
 */
public enum TaskLifecycle {
    CREATED,
    ACTIVE,
    WAITING,
    FINALIZING,
    CLOSED
}
