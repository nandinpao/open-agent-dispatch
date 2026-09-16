package com.opensocket.aievent.core.taskauthority;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.events.TaskTerminalEvent;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyAutomationStatus;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecision;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyOrchestrationService;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;

/**
 * Delivers the A0-R2 finalization Issue-projection outbox only after canonical CLOSED.
 *
 * <p>HF18: claim, business projection and terminal outbox state are deliberately isolated in
 * independent transactions. A projection failure may mark its own transaction rollback-only; the
 * FAILED/backoff write must therefore happen after that transaction has ended. DISPATCHED acts as
 * a bounded claim lease and is reclaimable after {@code next_attempt_at}.</p>
 */
@Service
public class TaskFinalizationProjectionService {
    private static final Logger log = LoggerFactory.getLogger(TaskFinalizationProjectionService.class);
    private final JdbcTemplate jdbc;
    private final TaskRepository tasks;
    private final IssuePolicyOrchestrationService issues;
    private final int maxAttempts;
    private final int claimSeconds;
    private final TransactionTemplate requiresNew;

    public TaskFinalizationProjectionService(
            JdbcTemplate jdbc,
            TaskRepository tasks,
            IssuePolicyOrchestrationService issues,
            PlatformTransactionManager transactionManager,
            @Value("${opendispatch.a0-r2.finalization.projection-max-attempts:20}") int maxAttempts,
            @Value("${opendispatch.a0-r2.finalization.projection-claim-seconds:60}") int claimSeconds) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.issues = issues;
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 100));
        this.claimSeconds = Math.max(15, Math.min(claimSeconds, 300));
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Processes at most one due outbox row. Never keeps retry bookkeeping in the projection tx. */
    public boolean projectOne(String tenant, String actor) {
        Item item = requiresNew.execute(tx -> claim(tenant, actor));
        if (item == null) return false;

        ProjectionResult result;
        try {
            result = requiresNew.execute(tx -> projectClaimed(tenant, actor, item));
            if (result == null) result = ProjectionResult.retry("ISSUE_PROJECTION_RESULT_MISSING");
        } catch (Exception ex) {
            String error = root(ex);
            log.warn("task_finalization_issue_projection_failed tenantId={} taskId={} outboxId={} attempt={} error={}",
                    tenant, item.taskId(), item.outboxId(), item.attempt(), error);
            result = ProjectionResult.retry(error);
        }

        ProjectionResult terminal = result;
        requiresNew.executeWithoutResult(tx -> {
            bind(tenant, actor);
            if (terminal.success()) acknowledge(tenant, item, terminal);
            else retry(tenant, item, terminal.error());
        });
        return true;
    }

    private Item claim(String tenant, String actor) {
        bind(tenant, actor);
        Item item = jdbc.query("""
          select outbox_id,task_id,attempt_count from task_finalization_projection_outbox
           where tenant_id=? and (
                 (status in ('PENDING','FAILED') and next_attempt_at<=now())
                 or (status='DISPATCHED' and next_attempt_at<=now())
           )
           order by next_attempt_at,created_at,outbox_id
           for update skip locked limit 1
          """, rs -> rs.next() ? new Item(rs.getString(1), rs.getString(2), rs.getInt(3) + 1) : null, tenant);
        if (item == null) return null;
        int updated = jdbc.update("""
          update task_finalization_projection_outbox
             set status='DISPATCHED',attempt_count=?,next_attempt_at=now()+(? * interval '1 second'),updated_at=now()
           where tenant_id=? and outbox_id=?
          """, item.attempt(), claimSeconds, tenant, item.outboxId());
        if (updated != 1) throw new IllegalStateException("TASK_FINALIZATION_PROJECTION_CLAIM_CAS_FAILED:" + item.outboxId());
        log.info("task_finalization_issue_projection_claimed tenantId={} taskId={} outboxId={} attempt={} claimSeconds={}",
                tenant, item.taskId(), item.outboxId(), item.attempt(), claimSeconds);
        return item;
    }

    private ProjectionResult projectClaimed(String tenant, String actor, Item item) {
        bind(tenant, actor);
        String status = jdbc.query("select status from task_finalization_projection_outbox where tenant_id=? and outbox_id=? for update",
                rs -> rs.next() ? rs.getString(1) : null, tenant, item.outboxId());
        if (!"DISPATCHED".equals(status)) {
            throw new IllegalStateException("TASK_FINALIZATION_PROJECTION_CLAIM_LOST:" + item.outboxId() + ":" + status);
        }

        TaskRecord task = tasks.findByTenantAndId(tenant, item.taskId())
                .orElseThrow(() -> new IllegalStateException("TASK_NOT_FOUND:" + item.taskId()));
        if (task.getTaskLifecycle() == null || !"CLOSED".equals(task.getTaskLifecycle().name())) {
            return ProjectionResult.retry("TASK_FINALIZATION_NOT_COMPLETE");
        }

        log.info("task_finalization_issue_projection_started tenantId={} taskId={} outboxId={} attempt={} taskStatus={} taskIssueSyncPolicy={} taskIssueSyncPolicySource={} issueSyncPolicyInheritanceMode={} issueSyncPolicyInheritedFromTaskId={}",
                tenant, task.getTaskId(), item.outboxId(), item.attempt(), task.getStatus(), task.getIssueSyncPolicy(),
                task.getIssueSyncPolicySource(), task.getIssueSyncPolicyInheritanceMode(), task.getIssueSyncPolicyInheritedFromTaskId());
        IssuePolicyDecision decision = issues.onTerminalEvent(event(task));
        log.info("task_finalization_issue_projection_completed tenantId={} taskId={} outboxId={} attempt={} decision={} reasonCode={} bindingStatus={} automationStatus={} adapterActionId={} lastErrorCode={}",
                tenant, task.getTaskId(), item.outboxId(), item.attempt(), decision.decision(), decision.reasonCode(),
                decision.bindingStatus(), decision.automationStatus(), decision.adapterActionId(), decision.lastErrorCode());
        if (decision.automationStatus() == IssuePolicyAutomationStatus.FAILED) {
            String failure = firstNonBlank(decision.lastErrorCode(), "ISSUE_AUTOMATION_FAILED") + ":"
                    + firstNonBlank(decision.lastErrorMessage(), "Route B automation failed without an error message");
            return ProjectionResult.retry(failure);
        }
        return ProjectionResult.success(decision.automationStatus(), decision.adapterActionId());
    }

    private void acknowledge(String tenant, Item item, ProjectionResult result) {
        int updated = jdbc.update("""
          update task_finalization_projection_outbox
             set status='ACKNOWLEDGED',updated_at=now()
           where tenant_id=? and outbox_id=? and status='DISPATCHED'
          """, tenant, item.outboxId());
        if (updated != 1) throw new IllegalStateException("TASK_FINALIZATION_PROJECTION_ACK_CAS_FAILED:" + item.outboxId());
        log.info("task_finalization_issue_projection_acknowledged tenantId={} taskId={} outboxId={} attempt={} automationStatus={} adapterActionId={}",
                tenant, item.taskId(), item.outboxId(), item.attempt(), result.automationStatus(), result.adapterActionId());
    }

    private void retry(String tenant, Item item, String error) {
        int attempt = item.attempt();
        if (attempt >= maxAttempts) {
            int updated = jdbc.update("""
              update task_finalization_projection_outbox
                 set status='SUPERSEDED',updated_at=now(),payload_json=payload_json||jsonb_build_object('lastError',?)
               where tenant_id=? and outbox_id=? and status='DISPATCHED'
              """, error, tenant, item.outboxId());
            if (updated != 1) throw new IllegalStateException("TASK_FINALIZATION_PROJECTION_DEAD_LETTER_CAS_FAILED:" + item.outboxId());
            log.error("task_finalization_issue_projection_retry_exhausted tenantId={} taskId={} outboxId={} attempt={} maxAttempts={} error={}",
                    tenant, item.taskId(), item.outboxId(), attempt, maxAttempts, error);
            return;
        }
        long seconds = Math.min(900L, 5L << Math.min(7, Math.max(0, attempt - 1)));
        int updated = jdbc.update("""
          update task_finalization_projection_outbox
             set status='FAILED',next_attempt_at=now()+(? * interval '1 second'),updated_at=now(),
                 payload_json=payload_json||jsonb_build_object('lastError',?)
           where tenant_id=? and outbox_id=? and status='DISPATCHED'
          """, seconds, error, tenant, item.outboxId());
        if (updated != 1) throw new IllegalStateException("TASK_FINALIZATION_PROJECTION_RETRY_CAS_FAILED:" + item.outboxId());
        log.warn("task_finalization_issue_projection_retry_scheduled tenantId={} taskId={} outboxId={} attempt={} maxAttempts={} backoffSeconds={} error={}",
                tenant, item.taskId(), item.outboxId(), attempt, maxAttempts, seconds, error);
    }

    private TaskTerminalEvent event(TaskRecord t) {
        OffsetDateTime at = t.getFinalizationCompletedAt() == null ? OffsetDateTime.now() : t.getFinalizationCompletedAt();
        return new TaskTerminalEvent(
                "evt-a0r2-finalized-" + UUID.randomUUID(), t.getTaskId(), t.getIncidentId(), t.getSourceEventId(),
                t.getStatus() == null ? null : t.getStatus().name(), t.getTaskType() == null ? null : t.getTaskType().name(),
                t.getPriority() == null ? null : t.getPriority().name(), t.getTenantId(), t.getSiteId(), t.getPlantId(),
                t.getObjectType(), t.getObjectId(), t.getEventType(), t.getErrorCode(), t.getRoutingPolicy(), t.getRequiredCapabilities(),
                null, null, null, null, null, null, "FINALIZATION", t.getTerminalizationReason() == null ? null : t.getTerminalizationReason().name(),
                t.getTaskOutcome() == null ? null : t.getTaskOutcome().name(), null, null,
                Map.of("canonicalLifecycle", "CLOSED", "outcome", t.getTaskOutcome() == null ? "" : t.getTaskOutcome().name()), at);
    }

    private void bind(String tenant, String actor) {
        jdbc.queryForObject("select set_config('app.current_tenant_id',?,true)", String.class, tenant);
        jdbc.queryForObject("select set_config('app.current_actor_id',?,true)", String.class, actor);
    }

    private static String root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) c = c.getCause();
        String m = c.getMessage();
        return m == null ? c.getClass().getSimpleName() : m;
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim();
    }

    private record Item(String outboxId, String taskId, int attempt) {}

    private record ProjectionResult(boolean success, String error, String automationStatus, String adapterActionId) {
        static ProjectionResult retry(String error) { return new ProjectionResult(false, error, null, null); }
        static ProjectionResult success(IssuePolicyAutomationStatus status, String adapterActionId) {
            return new ProjectionResult(true, null, status == null ? null : status.name(), adapterActionId);
        }
    }
}
