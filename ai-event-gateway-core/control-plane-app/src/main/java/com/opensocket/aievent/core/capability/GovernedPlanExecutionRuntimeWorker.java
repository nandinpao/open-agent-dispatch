package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Stage 9 durable dispatcher for server-authorized DISPATCH_READY Plan Steps. */
@Component
public class GovernedPlanExecutionRuntimeWorker {
    private static final Logger log = LoggerFactory.getLogger(GovernedPlanExecutionRuntimeWorker.class);

    private final JdbcTemplate plain;
    private final NamedParameterJdbcTemplate jdbc;
    private final GovernedPlanExecutionService plans;
    private final TransactionTemplate transactions;
    private final String workerId;
    private final int batchSize;

    public GovernedPlanExecutionRuntimeWorker(
            JdbcTemplate plain,
            NamedParameterJdbcTemplate jdbc,
            GovernedPlanExecutionService plans,
            PlatformTransactionManager transactionManager,
            @Value("${opendispatch.plan-runtime.worker-id:plan-runtime-worker}") String workerId,
            @Value("${opendispatch.plan-runtime.batch-size:20}") int batchSize) {
        this.plain = plain;
        this.jdbc = jdbc;
        this.plans = plans;
        this.transactions = new TransactionTemplate(transactionManager);
        this.workerId = workerId;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
    }

    @Scheduled(fixedDelayString = "${opendispatch.plan-runtime.poll-ms:1500}")
    public void run() {
        for (String tenant : plain.queryForList(
                "select tenant_id from tenants where status='ACTIVE' order by tenant_id", String.class)) {
            runTenant(tenant);
        }
    }

    private void runTenant(String tenant) {
        try (IamTenantContextHolder.Scope ignored = IamTenantContextHolder.open(
                new IamTenantExecutionContext(tenant, workerId))) {
            List<Item> claimed = transactions.execute(status -> claimInCurrentTransaction(tenant));
            if (claimed == null || claimed.isEmpty()) return;

            for (Item item : claimed) {
                try {
                    plans.submitStep(
                            tenant,
                            item.runId(),
                            item.stepId(),
                            new PlanStepSubmitRequest(
                                    "runtime:" + item.runId() + ":" + item.stepId() + ":" + item.attemptNo(),
                                    null));
                } catch (Exception ex) {
                    transactions.executeWithoutResult(status -> releaseInCurrentTransaction(
                            tenant, item.runId(), item.stepId()));
                    log.warn(
                            "plan_runtime_submit_failed tenant={} runId={} stepId={} attemptNo={} error={}",
                            tenant,
                            item.runId(),
                            item.stepId(),
                            item.attemptNo(),
                            safe(ex.getMessage()));
                }
            }
        } catch (Exception ex) {
            log.error("plan_runtime_tenant_scan_failed tenant={} error={}", tenant, safe(ex.getMessage()), ex);
        }
    }

    /**
     * Must execute inside {@link TransactionTemplate}. The PostgreSQL tenant settings below are
     * transaction-local by design; executing them outside a transaction would make them disappear
     * before the claim statement and FORCE-RLS would fail with TENANT_CONTEXT_REQUIRED.
     */
    private List<Item> claimInCurrentTransaction(String tenant) {
        bindDatabaseTenantContext(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime until = now.plusSeconds(45);
        return jdbc.query("""
          with due as (
            select s.run_id,s.step_id,s.attempt_count+1 attempt_no
              from plan_execution_steps s join plan_execution_runs r on r.tenant_id=s.tenant_id and r.run_id=s.run_id
             where s.tenant_id=:tenant and r.execution_mode='RUNTIME' and r.status='RUNNING' and s.state='DISPATCH_READY'
               and (s.runtime_claim_until is null or s.runtime_claim_until<:now)
             order by s.updated_at,s.step_id for update of s skip locked limit :limit
          )
          update plan_execution_steps s set runtime_claimed_by=:worker,runtime_claim_until=:until
            from due where s.tenant_id=:tenant and s.run_id=due.run_id and s.step_id=due.step_id
          returning s.run_id,s.step_id,s.attempt_count+1
          """,
                new MapSqlParameterSource("tenant", tenant)
                        .addValue("worker", workerId)
                        .addValue("until", until)
                        .addValue("now", now)
                        .addValue("limit", batchSize),
                (rs, n) -> new Item(rs.getString(1), rs.getString(2), rs.getInt(3)));
    }

    private void releaseInCurrentTransaction(String tenant, String run, String step) {
        bindDatabaseTenantContext(tenant);
        jdbc.update(
                "update plan_execution_steps set runtime_claimed_by=null,runtime_claim_until=null "
                        + "where tenant_id=:tenant and run_id=:run and step_id=:step and state='DISPATCH_READY'",
                new MapSqlParameterSource("tenant", tenant)
                        .addValue("run", run)
                        .addValue("step", step));
    }

    private void bindDatabaseTenantContext(String tenant) {
        IamTenantExecutionContext current = IamTenantContextHolder.current().orElseThrow(
                () -> new IllegalStateException("PLAN_RUNTIME_TENANT_CONTEXT_REQUIRED"));
        if (!tenant.equals(current.tenantId())) {
            throw new IllegalStateException("PLAN_RUNTIME_TENANT_CONTEXT_MISMATCH");
        }
        jdbc.getJdbcTemplate().queryForObject(
                "select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject(
                "select set_config('app.current_actor_id', ?, true)", String.class, workerId);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record Item(String runId, String stepId, int attemptNo) {}
}
