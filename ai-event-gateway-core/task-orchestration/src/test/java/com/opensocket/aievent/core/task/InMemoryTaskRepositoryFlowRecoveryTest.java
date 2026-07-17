package com.opensocket.aievent.core.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class InMemoryTaskRepositoryFlowRecoveryTest {

    @Test
    void capabilityFreeMatchedFlowIsNotReclaimedAsStale() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        OffsetDateTime now = OffsetDateTime.now();
        TaskRecord task = task("task-no-cap", now.minusMinutes(1));
        task.setRoutingPath("FLOW_RULE");
        task.setMatchedFlowId("flow-no-cap");
        task.setMatchedRuleId("rule-no-cap");
        task.setRequestedSkill(null);
        repository.save(task);

        List<TaskRecord> claimed = repository.claimDispatchRecoveryDue(
                "worker-stage6", now, now.plusMinutes(1), 10);

        assertThat(claimed).isEmpty();
    }

    @Test
    void configurationBlockedTaskIsNotReclaimedUntilConfigurationChanges() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        OffsetDateTime now = OffsetDateTime.now();
        TaskRecord task = task("task-blocked", now.minusMinutes(1));
        task.setRoutingPath("FLOW_RULE_REQUIRED_BLOCKED");
        repository.save(task);
        repository.suspendDispatchUntilConfigurationChange(
                task.getTaskId(), "NO_ACTIVE_FLOW_RULE", "No active Flow", now);

        assertThat(repository.claimDispatchRecoveryDue(
                "worker-stage6", now.plusSeconds(10), now.plusMinutes(1), 10)).isEmpty();

        assertThat(repository.wakeConfigurationBlockedTasks(
                "tenant-stage6", "SRC_STAGE6_RANDOM", now.plusSeconds(20), "Dispatch Flow configuration changed"))
                .isEqualTo(1);

        List<TaskRecord> claimed = repository.claimDispatchRecoveryDue(
                "worker-stage6", now.plusSeconds(21), now.plusMinutes(2), 10);
        assertThat(claimed).extracting(TaskRecord::getTaskId).containsExactly("task-blocked");
    }

    private TaskRecord task(String taskId, OffsetDateTime updatedAt) {
        TaskRecord task = new TaskRecord();
        task.setTaskId(taskId);
        task.setTenantId("tenant-stage6");
        task.setSourceSystem("SRC_STAGE6_RANDOM");
        task.setStatus(TaskStatus.QUEUED);
        task.setCreatedAt(updatedAt);
        task.setUpdatedAt(updatedAt);
        return task;
    }
}
