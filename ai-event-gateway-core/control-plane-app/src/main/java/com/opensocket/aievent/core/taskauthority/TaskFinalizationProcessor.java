package com.opensocket.aievent.core.taskauthority;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * A0-R2 single close authority.
 *
 * <p>Each finalization step owns an independent transaction. A completed/not-applicable step is
 * durable before the next step starts, so a node crash resumes from persisted step evidence instead
 * of replaying the entire pipeline. The canonical CLOSED transition is a separate final transaction.
 */
@Service
public class TaskFinalizationProcessor {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TaskOutcomeResolver resolver;
    private final TransactionTemplate transactions;
    private final int claimSeconds;

    public TaskFinalizationProcessor(
            JdbcTemplate jdbc,
            ObjectMapper json,
            TaskOutcomeResolver resolver,
            PlatformTransactionManager transactionManager,
            @Value("${opendispatch.a0-r2.finalization.claim-seconds:60}") int claimSeconds) {
        this.jdbc = jdbc;
        this.json = json;
        this.resolver = resolver;
        this.transactions = new TransactionTemplate(transactionManager);
        this.claimSeconds = Math.max(15, Math.min(claimSeconds, 300));
    }

    public void finalizeTask(String tenant, TaskFinalizationQueueService.Item item, String actor) {
        ResolutionSnapshot resolved = outcomeResolution(tenant, item.taskId(), actor);
        if (resolved == null) return;

        runStep(tenant, item.taskId(), actor, "AGGREGATION_PERSISTENCE", task -> aggregation(tenant, task));
        runStep(tenant, item.taskId(), actor, "EVIDENCE_PERSISTENCE", task -> evidence(tenant, task, resolved, item.attempt()));
        runStep(tenant, item.taskId(), actor, "CASE_PERSISTENCE", task -> casePersistence(tenant, task));
        runStep(tenant, item.taskId(), actor, "ISSUE_PROJECTION_OUTBOX", task -> issueOutbox(tenant, task, resolved));
        runStep(tenant, item.taskId(), actor, "BUDGET_SETTLEMENT", task -> Map.of("status", "NOT_APPLICABLE", "reason", "A0R2_NO_BUDGET_LEDGER_AUTHORITY"));
        runStep(tenant, item.taskId(), actor, "EXECUTION_MEMORY", task -> Map.of("status", "NOT_APPLICABLE", "reason", "A0R2_STAGE11_ROLLUP_REMAINS_MEMORY_PROJECTION_AUTHORITY"));
        runStep(tenant, item.taskId(), actor, "LEASE_RELEASE", task -> releaseLease(tenant, task));

        closeCanonicalTask(tenant, item.taskId(), actor, resolved);
    }

    private ResolutionSnapshot outcomeResolution(String tenant, String taskId, String actor) {
        return required(transactions.execute(tx -> {
            bind(tenant, actor);
            TaskRow task = loadForUpdate(tenant, taskId);
            if (task == null || !"FINALIZING".equals(task.lifecycle())) return null;
            requireClaim(task, actor);
            renewClaim(tenant, taskId, actor);

            ResolutionSnapshot existing = loadResolution(tenant, taskId);
            if (existing != null) {
                renewClaim(tenant, taskId, actor);
                return existing;
            }

            TaskOutcomeResolver.Resolution resolved = resolver.resolve(tenant, task.taskId(), task.reason(), task.legacyStatus());
            ResolutionSnapshot snapshot = new ResolutionSnapshot(
                    resolved.outcome(), TaskOutcomeResolver.VERSION, resolved.source(), safe(resolved.sourceValue()));
            recordStep(tenant, task, actor, "OUTCOME_RESOLUTION", "COMPLETED", Map.of(
                    "outcome", snapshot.outcome(),
                    "resolverVersion", snapshot.resolverVersion(),
                    "source", snapshot.source(),
                    "sourceValue", snapshot.sourceValue()));
            renewClaim(tenant, taskId, actor);
            return snapshot;
        }));
    }

    private void runStep(String tenant, String taskId, String actor, String stepName, Function<TaskRow, Map<String, ?>> operation) {
        transactions.executeWithoutResult(tx -> {
            bind(tenant, actor);
            TaskRow task = loadForUpdate(tenant, taskId);
            if (task == null || !"FINALIZING".equals(task.lifecycle())) return;
            requireClaim(task, actor);
            renewClaim(tenant, taskId, actor);

            if (isStepComplete(tenant, taskId, stepName)) {
                renewClaim(tenant, taskId, actor);
                return;
            }

            Map<String, ?> evidence = operation.apply(task);
            Object statusValue = evidence.get("status");
            String status = statusValue == null ? "COMPLETED" : String.valueOf(statusValue);
            recordStep(tenant, task, actor, stepName, status, evidence);
            renewClaim(tenant, taskId, actor);
        });
    }

    private Map<String, ?> aggregation(String tenant, TaskRow task) {
        Integer count = jdbc.queryForObject("""
          select count(*) from execution_plans p join plan_execution_runs r on r.tenant_id=p.tenant_id and r.plan_id=p.plan_id
           join plan_execution_convergence_decisions c on c.tenant_id=r.tenant_id and c.run_id=r.run_id where p.tenant_id=? and p.task_ref=?
          """, Integer.class, tenant, task.taskId());
        return Map.of("status", count != null && count > 0 ? "COMPLETED" : "NOT_APPLICABLE", "persistedConvergenceDecisions", count == null ? 0 : count);
    }

    private Map<String, ?> evidence(String tenant, TaskRow task, ResolutionSnapshot r, int attempt) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("taskId", task.taskId());
        ev.put("legacyStatus", task.legacyStatus());
        ev.put("terminalizationReason", task.reason());
        ev.put("resolvedOutcome", r.outcome());
        ev.put("resolverVersion", r.resolverVersion());
        ev.put("attempt", attempt);
        String payload = write(ev);
        jdbc.update("""
          insert into task_finalization_evidence(tenant_id,evidence_id,task_id,terminalization_reason,resolved_outcome,outcome_resolver_version,legacy_status,finalization_attempt,evidence_json)
          values(?,?,?,?,?,?,?,?,cast(? as jsonb)) on conflict(tenant_id,task_id) do nothing
          """, tenant, "finev-" + UUID.randomUUID(), task.taskId(), task.reason(), r.outcome(), r.resolverVersion(), task.legacyStatus(), attempt, payload);
        return Map.of("status", "COMPLETED", "baselineEvidence", "task_finalization_evidence", "fullEvidenceLedger", "A0R8");
    }

    private Map<String, ?> casePersistence(String tenant, TaskRow task) {
        Integer n = jdbc.queryForObject("select count(*) from enterprise_cases where tenant_id=? and source_task_ref=?", Integer.class, tenant, task.taskId());
        return Map.of(
                "status", n != null && n > 0 ? "COMPLETED" : "NOT_APPLICABLE",
                "existingCaseCount", n == null ? 0 : n,
                "reason", n != null && n > 0 ? "CASE_ALREADY_PERSISTED" : "NO_CASE_MATERIALIZATION_REQUIRED_IN_A0R2");
    }

    private Map<String, ?> issueOutbox(String tenant, TaskRow task, ResolutionSnapshot r) {
        String key = "a0r2:task-finalized:" + task.taskId();
        Map<String, Object> payload = Map.of(
                "taskId", task.taskId(),
                "outcome", r.outcome(),
                "terminalizationReason", task.reason(),
                "finalizedBy", "A0_R2_FINALIZATION_COORDINATOR");
        jdbc.update("""
          insert into task_finalization_projection_outbox(tenant_id,outbox_id,task_id,idempotency_key,payload_json,status)
          values(?,?,?,?,cast(? as jsonb),'WAITING_FINALIZATION') on conflict(tenant_id,idempotency_key) do nothing
          """, tenant, "finout-" + UUID.randomUUID(), task.taskId(), key, write(payload));
        // Do not wake the legacy terminal projection here. This step commits before canonical CLOSED.
        // The close transaction releases blocked legacy events only after the canonical close fields
        // have been written, eliminating a FINALIZING -> Issue materialization race.
        return Map.of("status", "COMPLETED", "idempotencyKey", key, "legacyIssuePipelineRelease", "AT_CANONICAL_CLOSE");
    }

    private Map<String, ?> releaseLease(String tenant, TaskRow task) {
        int n = jdbc.update("""
          update task_assignments set capacity_reserved=false,capacity_released_at=coalesce(capacity_released_at,now()),
                 lease_expires_at=case when lease_expires_at is null or lease_expires_at>now() then now() else lease_expires_at end,updated_at=now()
           where tenant_id=? and task_id=? and capacity_reserved=true
          """, tenant, task.taskId());
        return Map.of("status", "COMPLETED", "releasedReservations", n);
    }

    private void closeCanonicalTask(String tenant, String taskId, String actor, ResolutionSnapshot resolved) {
        transactions.executeWithoutResult(tx -> {
            bind(tenant, actor);
            TaskRow task = loadForUpdate(tenant, taskId);
            if (task == null || !"FINALIZING".equals(task.lifecycle())) return;
            requireClaim(task, actor);
            requirePipelineComplete(tenant, taskId);

            OffsetDateTime now = OffsetDateTime.now();
            jdbc.queryForObject("select set_config('app.a0_r2_finalizer','true',true)", String.class);
            int n = jdbc.update("""
              update tasks set task_outcome=?,outcome_resolver_version=?,finalization_state='COMPLETED',finalization_checkpoint='COMPLETE',
                 finalization_completed_at=?,finalization_claimed_by=null,finalization_claim_until=null,finalization_next_attempt_at=null,
                 finalization_last_error_code=null,finalization_last_error_message=null,task_lifecycle='CLOSED',task_phase='CLOSURE',
                 status=case ? when 'SUCCEEDED' then 'COMPLETED' when 'PARTIAL_SUCCEEDED' then 'COMPLETED'
                             when 'CANCELLED' then 'CANCELLED' else 'FAILED' end,updated_at=?
              where tenant_id=? and task_id=? and task_lifecycle='FINALIZING' and finalization_state='RUNNING' and finalization_claimed_by=?
              """, resolved.outcome(), resolved.resolverVersion(), now, resolved.outcome(), now, tenant, task.taskId(), actor);
            if (n != 1) throw new IllegalStateException("TASK_FINALIZATION_CLOSE_CAS_FAILED:" + task.taskId());

            int activated = jdbc.update("""
              update task_finalization_projection_outbox set status='PENDING',next_attempt_at=now(),updated_at=now()
               where tenant_id=? and task_id=? and status='WAITING_FINALIZATION'
              """, tenant, task.taskId());

            int released = jdbc.update("""
              update module_outbox_events set status='PENDING',attempt_count=0,next_attempt_at=now(),last_error=null,claimed_by=null,claim_until=null,updated_at=now()
               where tenant_id=? and task_id=? and event_type='task.terminal.v1' and status in ('RETRY_WAITING','DEAD_LETTER')
                 and coalesce(last_error,'') like '%%TASK_FINALIZATION_NOT_COMPLETE%%'
              """, tenant, task.taskId());

            event(tenant, task.taskId(), "FINALIZATION_COMPLETED", "COMPLETED", null, resolved.outcome(), task.reason(), actor,
                    Map.of("resolverVersion", resolved.resolverVersion(), "finalizationProjectionOutboxActivated", activated, "blockedTerminalEventsReleased", released));
            resolveCondition(tenant, task.taskId(), "FINALIZATION_RETRY_PENDING", actor, "Finalization completed");
            resolveCondition(tenant, task.taskId(), "EVIDENCE_PERSISTENCE_BLOCKED", actor, "Finalization completed");
            resolveCondition(tenant, task.taskId(), "CASE_PERSISTENCE_BLOCKED", actor, "Finalization completed");
        });
    }

    private void requirePipelineComplete(String tenant, String taskId) {
        Integer complete = jdbc.queryForObject("""
          select count(*) from task_finalization_steps
           where tenant_id=? and task_id=? and step_name in
             ('OUTCOME_RESOLUTION','AGGREGATION_PERSISTENCE','EVIDENCE_PERSISTENCE','CASE_PERSISTENCE','ISSUE_PROJECTION_OUTBOX','BUDGET_SETTLEMENT','EXECUTION_MEMORY','LEASE_RELEASE')
             and step_status in ('COMPLETED','NOT_APPLICABLE')
          """, Integer.class, tenant, taskId);
        if (complete == null || complete != 8) throw new IllegalStateException("TASK_FINALIZATION_PIPELINE_INCOMPLETE:" + taskId + ":" + complete);
    }

    private boolean isStepComplete(String tenant, String taskId, String step) {
        Integer n = jdbc.queryForObject("""
          select count(*) from task_finalization_steps where tenant_id=? and task_id=? and step_name=? and step_status in ('COMPLETED','NOT_APPLICABLE')
          """, Integer.class, tenant, taskId, step);
        return n != null && n > 0;
    }

    private ResolutionSnapshot loadResolution(String tenant, String taskId) {
        return jdbc.query("""
          select evidence_json->>'outcome',evidence_json->>'resolverVersion',evidence_json->>'source',evidence_json->>'sourceValue'
            from task_finalization_steps where tenant_id=? and task_id=? and step_name='OUTCOME_RESOLUTION' and step_status='COMPLETED'
          """, rs -> rs.next() ? new ResolutionSnapshot(rs.getString(1), rs.getString(2), rs.getString(3), safe(rs.getString(4))) : null, tenant, taskId);
    }

    private void recordStep(String tenant, TaskRow task, String actor, String name, String status, Map<String, ?> evidence) {
        int order = switch (name) {
            case "OUTCOME_RESOLUTION" -> 1;
            case "AGGREGATION_PERSISTENCE" -> 2;
            case "EVIDENCE_PERSISTENCE" -> 3;
            case "CASE_PERSISTENCE" -> 4;
            case "ISSUE_PROJECTION_OUTBOX" -> 5;
            case "BUDGET_SETTLEMENT" -> 6;
            case "EXECUTION_MEMORY" -> 7;
            case "LEASE_RELEASE" -> 8;
            default -> 99;
        };
        jdbc.update("""
          insert into task_finalization_steps(tenant_id,task_id,step_name,step_order,step_status,attempt_count,evidence_json,completed_at,updated_at)
          values(?,?,?,?,?,1,cast(? as jsonb),case when ? in ('COMPLETED','NOT_APPLICABLE') then now() else null end,now())
          on conflict(tenant_id,task_id,step_name) do update set step_status=excluded.step_status,attempt_count=task_finalization_steps.attempt_count+1,
              evidence_json=excluded.evidence_json,last_error_code=null,last_error_message=null,completed_at=excluded.completed_at,updated_at=now()
          """, tenant, task.taskId(), name, order, status, write(evidence), status);
        jdbc.update("update tasks set finalization_checkpoint=? where tenant_id=? and task_id=?", name, tenant, task.taskId());
        event(tenant, task.taskId(), "FINALIZATION_STEP", "RUNNING", name, null, task.reason(), actor, Map.of("stepStatus", status));
    }

    private TaskRow loadForUpdate(String tenant, String task) {
        return jdbc.query("""
          select task_id,status,task_lifecycle,terminalization_reason,finalization_claimed_by,finalization_claim_until
            from tasks where tenant_id=? and task_id=? for update
          """, rs -> rs.next() ? new TaskRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, OffsetDateTime.class)) : null,
          tenant, task);
    }

    private void requireClaim(TaskRow task, String actor) {
        if (!Objects.equals(actor, task.claimedBy())) throw new IllegalStateException("TASK_FINALIZATION_CLAIM_LOST:" + task.taskId());
    }

    private void renewClaim(String tenant, String taskId, String actor) {
        OffsetDateTime until = OffsetDateTime.now().plusSeconds(claimSeconds);
        int n = jdbc.update("""
          update tasks set finalization_claim_until=?,updated_at=now()
           where tenant_id=? and task_id=? and task_lifecycle='FINALIZING' and finalization_state='RUNNING' and finalization_claimed_by=?
          """, until, tenant, taskId, actor);
        if (n != 1) throw new IllegalStateException("TASK_FINALIZATION_CLAIM_LOST:" + taskId);
    }

    private void event(String tenant, String task, String type, String state, String step, String outcome, String reason, String actor, Map<String, ?> ev) {
        jdbc.update("insert into task_finalization_events(tenant_id,event_id,task_id,event_type,finalization_state,step_name,outcome,terminalization_reason,actor_ref,evidence_json) values(?,?,?,?,?,?,?,?,?,cast(? as jsonb))",
                tenant, "finevt-" + UUID.randomUUID(), task, type, state, step, outcome, reason, actor, write(ev));
    }

    private void resolveCondition(String tenant, String task, String type, String actor, String note) {
        jdbc.update("update task_conditions set condition_state='RESOLVED',resolved_by=?,resolved_at=now(),resolution_note=?,version=version+1 where tenant_id=? and task_id=? and condition_type=? and condition_state='ACTIVE'",
                actor, note, tenant, task, type);
    }

    private void bind(String tenant, String actor) {
        jdbc.queryForObject("select set_config('app.current_tenant_id',?,true)", String.class, tenant);
        jdbc.queryForObject("select set_config('app.current_actor_id',?,true)", String.class, actor);
    }

    private String write(Object v) {
        try { return json.writeValueAsString(v); }
        catch (Exception e) { throw new IllegalStateException("A0_R2_EVIDENCE_JSON_FAILED", e); }
    }

    private static <T> T required(T value) { return value; }
    private static String safe(String v) { return v == null ? "" : v; }

    private record TaskRow(String taskId, String legacyStatus, String lifecycle, String reason, String claimedBy, OffsetDateTime claimUntil) {}
    private record ResolutionSnapshot(String outcome, String resolverVersion, String source, String sourceValue) {}
}
