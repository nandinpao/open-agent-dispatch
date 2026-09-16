package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.GovernedManagedAgentAssignmentRequest;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskRecord;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 4 first concrete HOW adapter. It maps a selected MANAGED_AGENT provider to the explicit
 * governed Agent identity link, then delegates runtime assignment to the canonical Task
 * Orchestration path. It never performs provider routing itself.
 */
@Component
public class ManagedAgentNettyExecutionAdapter implements ExecutionAdapterPort {
    private final NamedParameterJdbcTemplate jdbc;
    private final TaskOrchestrationFacade tasks;
    private final TaskOperationalQuery taskQuery;

    public ManagedAgentNettyExecutionAdapter(NamedParameterJdbcTemplate jdbc, TaskOrchestrationFacade tasks, TaskOperationalQuery taskQuery) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.taskQuery = taskQuery;
    }

    @Override
    public String adapterType() {
        return "MANAGED_AGENT_NETTY";
    }

    @Override
    public ExecutionAdapterResult submit(ExecutionAdapterRegistration adapter, ExecutionAdapterCommand command) {
        if (adapter == null || command == null) throw new IllegalArgumentException("Managed Agent adapter command is required");
        if (!adapterType().equals(adapter.adapterType())) throw new IllegalArgumentException("MANAGED_AGENT_NETTY adapter required");
        if (!"MANAGED_AGENT".equals(adapter.providerType())) throw new IllegalArgumentException("MANAGED_AGENT provider required");
        TaskRecord child = taskQuery.findTask(command.tenantId(), command.localTaskId())
                .orElseThrow(() -> new IllegalArgumentException("Stage 4 delegated child Task not found: " + command.localTaskId()));
        boolean capabilityDelegation = "CAPABILITY_DELEGATION".equals(child.getTaskTypeCode())
                && "CAPABILITY_AUTHORITY_TO_CANONICAL_DISPATCH".equals(child.getRoutingPath());
        boolean planStep = "PLAN_EXECUTION_STEP".equals(child.getTaskTypeCode())
                && "PLAN_STEP_TO_CANONICAL_EXECUTION".equals(child.getRoutingPath());
        if ((!capabilityDelegation && !planStep) || child.getParentTaskId() == null || child.getParentTaskId().isBlank()) {
            return new ExecutionAdapterResult(false, "MANAGED_AGENT_SCOPE_REJECTED", null,
                    List.of("MANAGED_AGENT_ADAPTER_ACCEPTS_ONLY_GOVERNED_CAPABILITY_OR_PLAN_CHILD_TASKS"), OffsetDateTime.now());
        }
        String authorizationDecisionId = requiredInput(command.input().get("authorizationDecisionId"), "authorizationDecisionId");
        String routingDecisionId = requiredInput(command.input().get("routingDecisionId"), "routingDecisionId");
        String bindingId = requiredInput(command.input().get("bindingId"), "bindingId");
        String requestingAgentId = capabilityDelegation
                ? requiredInput(command.input().get("requestingAgentId"), "requestingAgentId")
                : optionalInput(command.input().get("requestingAgentId"));
        String agentId = resolveAgent(command.tenantId(), adapter.providerId());
        if (requestingAgentId != null && agentId.equals(requestingAgentId)) {
            return new ExecutionAdapterResult(false, "SELF_DELEGATION_REJECTED", null,
                    List.of("CAPABILITY_DELEGATION_REQUIRES_DISTINCT_MANAGED_AGENT_PROVIDER"), OffsetDateTime.now());
        }
        AssignmentDecisionResult result = tasks.assignGovernedManagedAgent(new GovernedManagedAgentAssignmentRequest(
                command.localTaskId(), agentId, bindingId, adapter.providerId(), authorizationDecisionId,
                routingDecisionId, command.executionResolutionId(),
                (planStep?"Stage 9 Plan Step":"Stage 4 capability runtime") + " selected managed provider=" + adapter.providerId()));
        if (result.assignmentId() == null || result.assignmentId().isBlank()) {
            return new ExecutionAdapterResult(false, "ASSIGNMENT_REJECTED", null,
                    List.of("CANONICAL_ASSIGNMENT_NOT_CREATED", safe(result.reason())), OffsetDateTime.now());
        }
        String externalRef = "assignment:" + result.assignmentId()
                + (result.dispatchRequestId() == null ? "" : ";dispatch:" + result.dispatchRequestId());
        if (!result.dispatchRequestCreated() && (result.dispatchRequestId() == null || result.dispatchRequestId().isBlank())) {
            return new ExecutionAdapterResult(false, "ASSIGNED_NO_DISPATCH", externalRef,
                    List.of("CANONICAL_DISPATCH_REQUEST_NOT_CREATED", safe(result.dispatchReason())), OffsetDateTime.now());
        }
        return new ExecutionAdapterResult(true, "DISPATCH_QUEUED", externalRef,
                List.of("CANONICAL_TASK_ASSIGNMENT_CREATED", "CANONICAL_DISPATCH_REQUEST_CREATED"), OffsetDateTime.now());
    }

    private String resolveAgent(String tenantId, String providerId) {
        try {
            return jdbc.queryForObject("""
                    select l.agent_id
                      from managed_agent_provider_links l
                      join capability_providers p on p.tenant_id=l.tenant_id and p.provider_id=l.provider_id
                      join agent_profiles a on a.tenant_id=l.tenant_id and a.agent_id=l.agent_id
                     where l.tenant_id=:tenant and l.provider_id=:provider
                       and l.status='ACTIVE'
                       and p.provider_type='MANAGED_AGENT' and p.catalog_status='REGISTERED'
                       and a.approval_status='APPROVED' and a.enabled=true
                    """, new MapSqlParameterSource("tenant", required(tenantId, "tenantId"))
                    .addValue("provider", required(providerId, "providerId")), String.class);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("MANAGED_AGENT_PROVIDER_LINK_NOT_ACTIVE: " + providerId);
        }
    }

    private static String optionalInput(Object value) {
        String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isBlank() ? null : text;
    }
    private static String requiredInput(Object value, String field) {
        String text = value == null ? null : String.valueOf(value).trim();
        if (text == null || text.isBlank()) throw new IllegalArgumentException(field + " execution evidence is required");
        return text;
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String safe(String value) { return value == null || value.isBlank() ? "UNSPECIFIED" : value.replaceAll("[\\r\\n]+", " ").trim(); }
}
