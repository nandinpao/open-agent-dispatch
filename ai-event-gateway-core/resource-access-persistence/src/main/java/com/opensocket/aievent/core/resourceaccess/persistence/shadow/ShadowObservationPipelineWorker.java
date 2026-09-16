package com.opensocket.aievent.core.resourceaccess.persistence.shadow;

import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Phase 5J database-backed worker. Queue claiming is performed with PostgreSQL
 * SKIP LOCKED, so every Control Plane node may run this worker safely.
 */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "resource-access.shadow-pipeline", name = "enabled", havingValue = "true")
public class ShadowObservationPipelineWorker {
    private final JdbcTemplate jdbc;
    private final String workerId;

    public ShadowObservationPipelineWorker(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();
    }

    @Scheduled(
            fixedDelayString = "${resource-access.shadow-pipeline.poll-delay-ms:250}",
            initialDelayString = "${resource-access.shadow-pipeline.initial-delay-ms:5000}")
    public void drain() {
        jdbc.queryForObject(
                "select phase5j_process_shadow_observation_batch(?,null)::text",
                String.class,
                workerId);
    }

    @Scheduled(
            fixedDelayString = "${resource-access.shadow-pipeline.partition-interval-ms:3600000}",
            initialDelayString = "${resource-access.shadow-pipeline.partition-initial-delay-ms:15000}")
    public void ensurePartitions() {
        LocalDate today = LocalDate.now();
        jdbc.queryForObject(
                "select phase5j_ensure_shadow_partitions(?,?)",
                Integer.class,
                today.minusDays(1),
                today.plusDays(7));
    }

    @Scheduled(
            fixedDelayString = "${resource-access.shadow-pipeline.retention-interval-ms:3600000}",
            initialDelayString = "${resource-access.shadow-pipeline.retention-initial-delay-ms:60000}")
    public void runRetention() {
        jdbc.queryForObject(
                "select phase5j_run_shadow_retention(?,?,?)::text",
                String.class,
                workerId,
                "Scheduled Phase 5J retention and partition archive execution",
                UUID.randomUUID().toString());
    }
}
