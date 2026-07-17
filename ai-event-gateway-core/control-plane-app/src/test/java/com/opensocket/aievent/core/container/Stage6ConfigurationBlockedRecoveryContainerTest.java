package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.database.persistence.task.dao.TaskDao;

/**
 * Stage 6 PostgreSQL integration contract for configuration-blocked recovery.
 *
 * <p>A Task waiting for an authoritative configuration change must have no due
 * retry time and therefore must not be claimed by the scheduled scanner. A
 * matching configuration mutation wakes the Task exactly once through the
 * ordinary due-time claim path.</p>
 */
class Stage6ConfigurationBlockedRecoveryContainerTest extends P25RepositoryDbContainerSupport {

    private static final String TENANT = "tenant-stage6-recovery";
    private static final String SOURCE = "SRC_STAGE6_RECOVERY";
    private static final String TASK_ID = "task-stage6-config-blocked";

    @Test
    void configurationBlockedTaskIsNotClaimedUntilMatchingConfigurationWakesIt() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0).minusMinutes(1);
        seedTask(createdAt);
        TaskDao tasks = mybatis.mapper(TaskDao.class);

        assertThat(tasks.suspendDispatchUntilConfigurationChange(
                TASK_ID,
                "NO_ACTIVE_FLOW_RULE",
                "No active Dispatch Flow Rule matched",
                createdAt.plusSeconds(5)))
                .isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "select next_dispatch_attempt_at is null from tasks where task_id=?",
                Boolean.class,
                TASK_ID)).isTrue();
        assertThat(jdbc.queryForObject(
                "select dispatch_retry_reason from tasks where task_id=?",
                String.class,
                TASK_ID)).startsWith("WAITING_CONFIGURATION:NO_ACTIVE_FLOW_RULE:");

        OffsetDateTime scannerNow = createdAt.plusMinutes(5);
        assertThat(tasks.claimDispatchRecoveryDue(
                "stage6-scanner-before-wake",
                scannerNow,
                scannerNow.plusSeconds(30),
                10)).isEmpty();

        assertThat(tasks.wakeConfigurationBlockedTasks(
                TENANT,
                "ANOTHER_SOURCE",
                scannerNow,
                "Unrelated Flow changed"))
                .isZero();
        assertThat(tasks.claimDispatchRecoveryDue(
                "stage6-scanner-wrong-source",
                scannerNow,
                scannerNow.plusSeconds(30),
                10)).isEmpty();

        assertThat(tasks.wakeConfigurationBlockedTasks(
                TENANT,
                SOURCE,
                scannerNow,
                "Matching Dispatch Flow configuration changed"))
                .isEqualTo(1);

        var firstClaim = tasks.claimDispatchRecoveryDue(
                "stage6-scanner-after-wake",
                scannerNow,
                scannerNow.plusSeconds(30),
                10);
        assertThat(firstClaim).extracting(task -> task.getTaskId()).containsExactly(TASK_ID);

        assertThat(tasks.claimDispatchRecoveryDue(
                "stage6-second-scanner",
                scannerNow,
                scannerNow.plusSeconds(30),
                10)).isEmpty();
    }

    private void seedTask(OffsetDateTime now) {
        jdbc.update("""
                insert into incidents(
                    incident_id, fingerprint, tenant_id, source_system, severity, status,
                    first_seen_at, last_seen_at, occurrence_count, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "incident-stage6-recovery",
                "fp-stage6-recovery",
                TENANT,
                SOURCE,
                "HIGH",
                "ACTIVE",
                now,
                now,
                1,
                now,
                now);
        jdbc.update("""
                insert into tasks(
                    task_id, incident_id, source_system, event_stage, task_type, status,
                    priority, tenant_id, routing_path, routing_policy,
                    required_capabilities_json, occurrence_count_at_creation,
                    created_at, updated_at, next_dispatch_attempt_at,
                    dispatch_attempt_count, lifecycle_reason)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?)
                """,
                TASK_ID,
                "incident-stage6-recovery",
                SOURCE,
                "EXTERNAL",
                "INCIDENT_RESPONSE",
                "QUEUED",
                "HIGH",
                TENANT,
                "FLOW_RULE_REQUIRED_BLOCKED",
                "AUTO",
                "[]",
                1,
                now,
                now,
                now,
                0,
                "Initial Stage 6 characterization state");
    }
}
