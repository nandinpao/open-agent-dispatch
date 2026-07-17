package com.opensocket.aievent.core.observability;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * P12 Micrometer metrics for Agent remediation workflow governance.
 *
 * <p>Keep tags deliberately low-cardinality. Do not tag agentId, workflowId,
 * idempotencyKey, operatorId or leaseOwner in Prometheus metrics. Those values
 * remain in workflow/history records and audit logs, while metrics stay safe for
 * long-lived production scraping.</p>
 */
@Service
public class AgentRemediationWorkflowMetricsService {
    private final MeterRegistry meterRegistry;
    private final ObservabilityProperties properties;

    public AgentRemediationWorkflowMetricsService(MeterRegistry meterRegistry, ObservabilityProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties == null ? new ObservabilityProperties() : properties;
    }

    public void recordWorkflowCreated(String status, String severity, boolean approvalRequired, int actionCount) {
        if (!enabled()) return;
        counter("aeg.core.remediation.workflows.created.total",
                tags("status", status,
                        "severity", severity,
                        "approval_required", Boolean.toString(approvalRequired),
                        "action_count_bucket", actionCountBucket(actionCount))).increment();
    }

    public void recordWorkflowDecision(String eventType, String fromStatus, String toStatus, String severity) {
        if (!enabled()) return;
        counter("aeg.core.remediation.workflows.decisions.total",
                tags("event_type", eventType, "from_status", fromStatus, "to_status", toStatus, "severity", severity)).increment();
    }

    public void recordApprovalLatency(String severity, OffsetDateTime createdAt, OffsetDateTime decidedAt) {
        if (!enabled() || createdAt == null || decidedAt == null || decidedAt.isBefore(createdAt)) return;
        Timer.builder("aeg.core.remediation.workflows.approval.latency")
                .description("Time between remediation workflow creation and approval/rejection decision")
                .tags(tags("severity", severity))
                .register(meterRegistry)
                .record(Duration.between(createdAt, decidedAt));
    }

    public void recordWorkflowExecution(String finalStatus,
                                        String severity,
                                        boolean dryRun,
                                        long successCount,
                                        long skippedCount,
                                        long failureCount) {
        if (!enabled()) return;
        counter("aeg.core.remediation.workflows.executions.total",
                tags("final_status", finalStatus,
                        "severity", severity,
                        "dry_run", Boolean.toString(dryRun),
                        "has_failure", Boolean.toString(failureCount > 0),
                        "has_skipped", Boolean.toString(skippedCount > 0),
                        "has_success", Boolean.toString(successCount > 0))).increment();
    }

    public void recordActionExecution(String actionType,
                                      String actionResult,
                                      boolean success,
                                      boolean skipped,
                                      Integer attemptCount) {
        if (!enabled()) return;
        counter("aeg.core.remediation.workflow.actions.executions.total",
                tags("action_type", actionType,
                        "action_result", actionResult,
                        "success", Boolean.toString(success),
                        "skipped", Boolean.toString(skipped),
                        "attempt_bucket", attemptBucket(attemptCount))).increment();
    }

    public void recordWorkflowLeaseEvent(String eventType) {
        if (!enabled()) return;
        counter("aeg.core.remediation.workflow.execution.lease.events.total",
                tags("event_type", eventType)).increment();
    }

    public void recordStaleLeaseRecoveryRun(int scannedCount, int recoveredCount, int raceLostCount, boolean scheduled) {
        if (!enabled()) return;
        counter("aeg.core.remediation.workflow.stale_leases.recovery_runs.total",
                tags("scheduled", Boolean.toString(scheduled),
                        "has_recovered", Boolean.toString(recoveredCount > 0),
                        "has_race_lost", Boolean.toString(raceLostCount > 0))).increment();
        if (scannedCount > 0) {
            counter("aeg.core.remediation.workflow.stale_leases.scanned.total",
                    tags("scheduled", Boolean.toString(scheduled))).increment(scannedCount);
        }
        if (recoveredCount > 0) {
            counter("aeg.core.remediation.workflow.stale_leases.recovered.total",
                    tags("scheduled", Boolean.toString(scheduled))).increment(recoveredCount);
        }
        if (raceLostCount > 0) {
            counter("aeg.core.remediation.workflow.stale_leases.race_lost.total",
                    tags("scheduled", Boolean.toString(scheduled))).increment(raceLostCount);
        }
    }

    private boolean enabled() {
        return properties.isEnabled()
                && properties.isBusinessMetricsEnabled()
                && properties.getRemediationWorkflowMetrics().isEnabled();
    }

    private Counter counter(String name, Iterable<io.micrometer.core.instrument.Tag> tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private Tags tags(String... keyValues) {
        Tags tags = Tags.empty();
        if (keyValues == null) return tags;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            tags = tags.and(safeTag(keyValues[i]), tag(keyValues[i + 1]));
        }
        return tags;
    }

    private String safeTag(String key) {
        return key == null || key.isBlank() ? "unknown" : key.trim();
    }

    private String tag(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String trimmed = value.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    private String actionCountBucket(int count) {
        if (count <= 0) return "0";
        if (count == 1) return "1";
        if (count <= 3) return "2_3";
        if (count <= 5) return "4_5";
        return "6_plus";
    }

    private String attemptBucket(Integer attemptCount) {
        int value = attemptCount == null ? 0 : attemptCount;
        if (value <= 0) return "0";
        if (value == 1) return "1";
        if (value == 2) return "2";
        if (value <= 5) return "3_5";
        return "6_plus";
    }
}
