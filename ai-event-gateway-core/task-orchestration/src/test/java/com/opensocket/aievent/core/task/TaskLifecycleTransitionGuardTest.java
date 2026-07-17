package com.opensocket.aievent.core.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaskLifecycleTransitionGuardTest {
    @Test
    void canonicalTodo15StatesShouldBeDeclared() {
        assertThat(TaskLifecycleTransitionGuard.canonicalStates())
                .containsExactlyInAnyOrder(
                        TaskStatus.QUEUED,
                        TaskStatus.ASSIGNED,
                        TaskStatus.RUNNING,
                        TaskStatus.RETRY_WAIT,
                        TaskStatus.SUCCEEDED,
                        TaskStatus.FAILED,
                        TaskStatus.ESCALATED,
                        TaskStatus.DEAD_LETTER,
                        TaskStatus.ORPHANED,
                        TaskStatus.RECONCILING);
    }

    @Test
    void shouldAllowCanonicalHappyPathAndRetryPath() {
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.QUEUED, TaskStatus.ASSIGNED)).isTrue();
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.ASSIGNED, TaskStatus.RUNNING)).isTrue();
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.RUNNING, TaskStatus.SUCCEEDED)).isTrue();
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.RUNNING, TaskStatus.RETRY_WAIT)).isTrue();
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.RETRY_WAIT, TaskStatus.QUEUED)).isTrue();
    }

    @Test
    void shouldAllowDispatchFailurePathToDeadLetter() {
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.ASSIGNED, TaskStatus.DEAD_LETTER)).isTrue();
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.DISPATCHED, TaskStatus.DEAD_LETTER)).isTrue();
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.RUNNING, TaskStatus.DEAD_LETTER)).isTrue();
    }

    @Test
    void shouldAllowOrphanReconcilingPath() {
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.ASSIGNED, TaskStatus.ORPHANED)).isTrue();
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.ORPHANED, TaskStatus.RECONCILING)).isTrue();
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.RECONCILING, TaskStatus.QUEUED)).isTrue();
    }

    @Test
    void shouldBlockTerminalSucceededMutation() {
        assertThat(TaskLifecycleTransitionGuard.canTransition(TaskStatus.SUCCEEDED, TaskStatus.RUNNING)).isFalse();
        assertThatThrownBy(() -> TaskLifecycleTransitionGuard.requireTransition(TaskStatus.SUCCEEDED, TaskStatus.RUNNING, "task-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal task lifecycle transition");
    }

    @Test
    void legacyStatusesShouldHaveCanonicalEquivalents() {
        assertThat(TaskStatus.CREATED.canonical()).isEqualTo(TaskStatus.QUEUED);
        assertThat(TaskStatus.DISPATCHED.canonical()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(TaskStatus.COMPLETED.canonical()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(TaskStatus.TIMED_OUT.canonical()).isEqualTo(TaskStatus.FAILED);
    }
}
