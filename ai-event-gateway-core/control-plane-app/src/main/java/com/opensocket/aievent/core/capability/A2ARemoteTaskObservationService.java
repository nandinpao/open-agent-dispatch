package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C0-C1/C0-C2 convergence point for POLL, STREAM, PUSH, CANCEL and reconciliation observations.
 * Non-authoritative events are durable evidence only. C0-C2 additionally routes every terminal
 * outcome through one fenced tracking-row CAS before canonical completion can run.
 */
@Service
public class A2ARemoteTaskObservationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final A2ARemoteEventJournalService journal;
    private final A2ARemoteTaskTrackingService tracking;
    private final A2ARemoteInterfaceRuntimeService runtime;
    private final ProviderNeutralExecutionCompletionRouter completion;
    private final A2APeerErrorMappingService errors;

    public A2ARemoteTaskObservationService(
            NamedParameterJdbcTemplate jdbc,
            A2ARemoteEventJournalService journal,
            A2ARemoteTaskTrackingService tracking,
            A2ARemoteInterfaceRuntimeService runtime,
            ProviderNeutralExecutionCompletionRouter completion,
            A2APeerErrorMappingService errors) {
        this.jdbc = jdbc;
        this.journal = journal;
        this.tracking = tracking;
        this.runtime = runtime;
        this.completion = completion;
        this.errors = errors;
    }

    @Transactional
    public ObservationResult observe(
            String tenant,
            A2ARemoteTaskTrackingService.RemoteTrackingLease lease,
            String source,
            Map<String, Object> payload,
            String remoteEventId,
            A2ARemoteAuthorityService.EventAuthorityContext authorityContext) {
        return observe(tenant, lease, source, payload, remoteEventId, authorityContext, true);
    }

    @Transactional
    public ObservationResult observe(
            String tenant,
            A2ARemoteTaskTrackingService.RemoteTrackingLease lease,
            String source,
            Map<String, Object> payload,
            String remoteEventId,
            A2ARemoteAuthorityService.EventAuthorityContext authorityContext,
            boolean releaseLeaseAfterApply) {
        bind(tenant);
        A2ARemoteInterfaceRuntimeService.ExecutionRuntime execution = runtime.execution(tenant, lease.executionId());
        if (execution == null) {
            return new ObservationResult(false, false, "EXECUTION_NOT_FOUND");
        }
        Map<String, Object> task = A2AProtocolObjects.task(payload);
        String state = A2AProtocolObjects.state(task);
        String type = eventType(payload, task);
        A2ARemoteEventJournalService.AppendResult appended = journal.append(
                tenant,
                lease.trackingId(),
                lease.executionId(),
                lease.remoteTaskId(),
                source,
                type,
                state,
                remoteEventId,
                payload,
                authorityContext);
        if (!appended.appended()) {
            return new ObservationResult(false, false, appended.authorityReason());
        }
        if (!appended.authoritative()) {
            // C0-C1: evidence exists, but canonical execution/tracking/delegation state is untouched.
            return new ObservationResult(true, false, appended.authorityReason());
        }
        if (state == null) {
            touch(tenant, lease.executionId());
            if (releaseLeaseAfterApply) {
                tracking.releaseIfOwned(tenant, lease);
            }
            return new ObservationResult(true, true, appended.authorityReason());
        }
        if (A2AProtocolObjects.terminal(state)) {
            boolean applied = terminal(tenant, lease, execution, state, payload, source, appended.journalEventId());
            return applied
                    ? new ObservationResult(true, true, appended.authorityReason())
                    : new ObservationResult(true, false, "TERMINAL_CONVERGENCE_LOST");
        }
        boolean applied = tracking.observed(tenant, lease, source, state, false, releaseLeaseAfterApply);
        if (!applied) {
            return new ObservationResult(true, false, "AUTHORITY_LOST_BEFORE_STATE_APPLY");
        }
        if (A2AProtocolObjects.interrupted(state) && execution.delegationId() != null) {
            markInterrupted(tenant, execution.delegationId(), state);
        }
        return new ObservationResult(true, true, appended.authorityReason());
    }

    protected boolean terminal(
            String tenant,
            A2ARemoteTaskTrackingService.RemoteTrackingLease lease,
            A2ARemoteInterfaceRuntimeService.ExecutionRuntime execution,
            String state,
            Map<String, Object> payload,
            String source,
            String journalEventId) {
        bind(tenant);
        OffsetDateTime now = OffsetDateTime.now();
        boolean ok = A2AProtocolObjects.success(state);
        if (ok) {
            A2ARemoteTaskTrackingService.TerminalDecision decision = tracking.terminalize(
                    tenant, lease, state, "SUCCEEDED", source, journalEventId);
            if (!decision.won()) {
                return false;
            }
            jdbc.update("""
                    update a2a_remote_read_executions
                       set status='SUCCEEDED',remote_state=:state,tracking_status='TERMINAL',completed_at=:now,
                           terminal_at=:now,last_remote_event_at=:now,updated_at=:now
                     where tenant_id=:tenant and execution_id=:execution and remote_terminal_outcome='SUCCEEDED'
                    """, new MapSqlParameterSource("tenant", tenant)
                    .addValue("execution", lease.executionId())
                    .addValue("state", state)
                    .addValue("now", now));
            completion.complete(
                    tenant,
                    execution.executionContextType(),
                    execution.delegationId(),
                    execution.planRunId(),
                    execution.planStepId(),
                    execution.taskId(),
                    "REMOTE_A2A_AGENT",
                    execution.providerId(),
                    lease.executionId(),
                    true,
                    payload,
                    null,
                    null);
            return true;
        }

        A2APeerErrorMappingService.Resolution resolution = errors.resolveAndRecord(
                tenant,
                lease.executionId(),
                lease.trackingId(),
                lease.peerId(),
                lease.interfaceId(),
                "TASK_TERMINAL",
                200,
                payload,
                "Remote A2A terminal state=" + state);
        A2ARemoteTaskTrackingService.TerminalDecision decision = tracking.terminalMappedFailure(
                tenant, lease, state, resolution, source, journalEventId);
        if (!decision.won()) {
            return false;
        }
        String executionStatus = "CANCELED".equals(decision.outcome())
                ? "FAILED"
                : (resolution.blocking() ? "BLOCKED" : "FAILED");
        jdbc.update("""
                update a2a_remote_read_executions
                   set status=:status,remote_state=:state,tracking_status='TERMINAL',completed_at=:now,terminal_at=:now,
                       last_remote_event_at=:now,error_code=:code,error_message=:message,remote_error_code=:remoteCode,
                       remote_error_message=:message,canonical_error_class=:errorClass,error_disposition=:disposition,
                       error_mapping_source=:mappingSource,error_mapping_override_id=:override,updated_at=:now
                 where tenant_id=:tenant and execution_id=:execution and remote_terminal_outcome=:terminalOutcome
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("execution", lease.executionId())
                .addValue("status", executionStatus)
                .addValue("state", state)
                .addValue("terminalOutcome", decision.outcome())
                .addValue("now", now)
                .addValue("code", resolution.canonicalErrorCode())
                .addValue("message", resolution.remoteErrorMessage())
                .addValue("remoteCode", resolution.remoteErrorCode())
                .addValue("errorClass", resolution.resolvedErrorClass())
                .addValue("disposition", resolution.disposition())
                .addValue("mappingSource", resolution.mappingSource())
                .addValue("override", resolution.overrideId()));
        completion.complete(
                tenant,
                execution.executionContextType(),
                execution.delegationId(),
                execution.planRunId(),
                execution.planStepId(),
                execution.taskId(),
                "REMOTE_A2A_AGENT",
                execution.providerId(),
                lease.executionId(),
                false,
                payload,
                resolution.canonicalErrorCode(),
                resolution.remoteErrorMessage());
        return true;
    }

    protected void markInterrupted(String tenant, String delegation, String state) {
        bind(tenant);
        jdbc.update("""
                update capability_delegation_requests
                   set status='WAITING_APPROVAL',reason_codes_json=cast(:reasons as jsonb),updated_at=:now
                 where tenant_id=:tenant and delegation_id=:delegation
                   and status not in ('RESULT_SUCCEEDED','RESULT_FAILED')
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("delegation", delegation)
                .addValue("reasons", "[\"REMOTE_" + state + "\"]")
                .addValue("now", OffsetDateTime.now()));
    }

    protected void touch(String tenant, String execution) {
        bind(tenant);
        jdbc.update("""
                update a2a_remote_read_executions
                   set last_remote_event_at=:now,updated_at=:now
                 where tenant_id=:tenant and execution_id=:execution
                """, new MapSqlParameterSource("tenant", tenant)
                .addValue("execution", execution)
                .addValue("now", OffsetDateTime.now()));
    }

    private String eventType(Map<String, Object> body, Map<String, Object> task) {
        if (body.containsKey("statusUpdate")) return "STATUS_UPDATE";
        if (body.containsKey("artifactUpdate")) return "ARTIFACT_UPDATE";
        if (body.containsKey("message")) return "MESSAGE";
        return task.isEmpty() ? "UNKNOWN" : "TASK_SNAPSHOT";
    }

    private void bind(String tenant) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, "a2a-observation");
    }

    public record ObservationResult(boolean appended, boolean authoritative, String reason) {}
}
