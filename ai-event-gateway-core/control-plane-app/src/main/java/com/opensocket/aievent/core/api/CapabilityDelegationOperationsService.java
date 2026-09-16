package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * C9 read-only projection for the capability-first delegation runtime.
 *
 * <p>This service never authorizes, ranks, selects, dispatches, or mutates a delegation. It only
 * projects already persisted authority evidence from the WHO CAN -> WHO MAY -> WHO SHOULD -> HOW
 * runtime chain. The complete historical WHO CAN candidate set was not persisted by the current
 * runtime contract, so this projection explicitly reports that limitation instead of reconstructing
 * history from today's provider catalog.</p>
 */
@Service
public class CapabilityDelegationOperationsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public CapabilityDelegationOperationsService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Page search(String tenantId, String status, String capabilityCode, String providerType,
            String parentTaskId, String correlationId, String requestingAgentId, String text,
            int offset, int limit, String sortDirection) {
        String tenant = required(tenantId, "tenantId");
        bindDatabaseTenantContext(tenant);
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String order = "ASC".equalsIgnoreCase(sortDirection) ? "asc" : "desc";
        String normalizedStatus = trim(status);
        String normalizedCapability = trim(capabilityCode);
        String normalizedProviderType = trim(providerType);
        String normalizedParentTaskId = trim(parentTaskId);
        String normalizedCorrelationId = trim(correlationId);
        String normalizedRequestingAgentId = trim(requestingAgentId);
        String normalizedText = trim(text);
        MapSqlParameterSource params = new MapSqlParameterSource("tenant", tenant)
                .addValue("offset", safeOffset)
                .addValue("limit", safeLimit);
        StringBuilder whereBuilder = new StringBuilder("""
                from capability_delegation_requests d
                left join capability_providers p on p.tenant_id=d.tenant_id and p.provider_id=d.selected_provider_id
                where d.tenant_id=:tenant
                """);
        if (normalizedStatus != null) {
            whereBuilder.append(" and d.status=:status\n");
            params.addValue("status", normalizedStatus);
        }
        if (normalizedCapability != null) {
            whereBuilder.append(" and d.capability_code=:capability\n");
            params.addValue("capability", normalizedCapability);
        }
        if (normalizedProviderType != null) {
            whereBuilder.append(" and d.selected_provider_type=:providerType\n");
            params.addValue("providerType", normalizedProviderType);
        }
        if (normalizedParentTaskId != null) {
            whereBuilder.append(" and d.parent_task_id=:parentTaskId\n");
            params.addValue("parentTaskId", normalizedParentTaskId);
        }
        if (normalizedCorrelationId != null) {
            whereBuilder.append(" and d.correlation_id=:correlationId\n");
            params.addValue("correlationId", normalizedCorrelationId);
        }
        if (normalizedRequestingAgentId != null) {
            whereBuilder.append(" and d.requesting_agent_id=:requestingAgentId\n");
            params.addValue("requestingAgentId", normalizedRequestingAgentId);
        }
        if (normalizedText != null) {
            whereBuilder.append(" and lower(concat_ws(' ',d.delegation_id,d.parent_task_id,d.child_task_id,d.requesting_agent_id,d.correlation_id,d.capability_code,d.operation,d.selected_provider_id,p.display_name,d.status)) like lower(concat('%',:text,'%'))\n");
            params.addValue("text", normalizedText);
        }
        String where = whereBuilder.toString();
        Integer total = jdbc.queryForObject("select count(*) " + where, params, Integer.class);
        List<ListItem> items = jdbc.query("""
                select d.delegation_id,d.parent_task_id,d.child_task_id,d.capability_code,d.operation,d.status,
                       d.selected_provider_id,coalesce(p.display_name,d.selected_provider_id) selected_provider_name,
                       d.selected_provider_type,d.execution_kind,d.result_status,d.result_notification_status,
                       d.created_at,d.updated_at
                """ + where + " order by d.updated_at " + order + ",d.delegation_id " + order + " offset :offset limit :limit",
                params, (rs, n) -> new ListItem(
                        rs.getString("delegation_id"), rs.getString("parent_task_id"), rs.getString("child_task_id"),
                        rs.getString("capability_code"), rs.getString("operation"), rs.getString("status"),
                        rs.getString("selected_provider_id"), rs.getString("selected_provider_name"),
                        rs.getString("selected_provider_type"), rs.getString("execution_kind"),
                        rs.getString("result_status"), rs.getString("result_notification_status"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)));
        return new Page(items, total == null ? 0 : total, safeOffset, safeLimit);
    }

    @Transactional(readOnly = true)
    public Detail detail(String tenantId, String delegationId) {
        String tenant = required(tenantId, "tenantId");
        bindDatabaseTenantContext(tenant);
        String id = required(delegationId, "delegationId");
        MapSqlParameterSource p = new MapSqlParameterSource("tenant", tenant).addValue("id", id);
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    select d.*,coalesce(cp.display_name,d.selected_provider_id) selected_provider_name
                    from capability_delegation_requests d
                    left join capability_providers cp on cp.tenant_id=d.tenant_id and cp.provider_id=d.selected_provider_id
                    where d.tenant_id=:tenant and d.delegation_id=:id
                    """, p);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Capability delegation not found: " + id);
        }
        ListItem summary = new ListItem(
                str(row, "delegation_id"), str(row, "parent_task_id"), str(row, "child_task_id"),
                str(row, "capability_code"), str(row, "operation"), str(row, "status"),
                str(row, "selected_provider_id"), str(row, "selected_provider_name"),
                str(row, "selected_provider_type"), str(row, "execution_kind"), str(row, "result_status"),
                str(row, "result_notification_status"), time(row, "created_at"), time(row, "updated_at"));

        DecisionEvidence whoMay = authorization(tenant, str(row, "authorization_decision_id"));
        DecisionEvidence whoShould = routing(tenant, str(row, "routing_decision_id"));
        DecisionEvidence how = adapter(tenant, str(row, "adapter_resolution_id"));
        List<DecisionStage> stages = List.of(
                whoCanStage(row, whoShould),
                stage("WHO_MAY", "Who may execute", whoMay, whoMayFallback(row)),
                stage("WHO_SHOULD", "Who should execute", whoShould, routingFallback(row)),
                stage("HOW", "How it will execute", how, adapterFallback(row)),
                executionStage(row));

        List<EventEvidence> events = jdbc.query("""
                select event_id,event_type,from_status,to_status,reason_codes_json,evidence_json,occurred_at
                from capability_delegation_events
                where tenant_id=:tenant and delegation_id=:id
                order by occurred_at,event_id
                """, p, (rs, n) -> new EventEvidence(
                        rs.getString("event_id"), rs.getString("event_type"), rs.getString("from_status"),
                        rs.getString("to_status"), readStrings(rs.getString("reason_codes_json")),
                        readMap(rs.getString("evidence_json")), rs.getObject("occurred_at", OffsetDateTime.class)));

        Map<String, String> request = map(
                "delegationId", str(row, "delegation_id"),
                "parentTaskId", str(row, "parent_task_id"),
                "requestingAgentId", str(row, "requesting_agent_id"),
                "capabilityCode", str(row, "capability_code"),
                "operation", str(row, "operation"),
                "sensitivityLevel", str(row, "sensitivity_level"),
                "correlationId", str(row, "correlation_id"),
                "reason", str(row, "reason"));
        Map<String, String> execution = map(
                "selectedBindingId", str(row, "selected_binding_id"),
                "selectedProviderId", str(row, "selected_provider_id"),
                "selectedProviderName", str(row, "selected_provider_name"),
                "selectedProviderType", str(row, "selected_provider_type"),
                "executionKind", str(row, "execution_kind"),
                "childTaskId", str(row, "child_task_id"),
                "assignmentId", str(row, "assignment_id"),
                "dispatchRequestId", str(row, "dispatch_request_id"),
                "resultStatus", str(row, "result_status"),
                "resultNotificationStatus", str(row, "result_notification_status"));
        return new Detail(summary, request, execution, readStrings(str(row, "reason_codes_json")), stages, events);
    }

    private DecisionStage whoCanStage(Map<String, Object> row, DecisionEvidence routing) {
        String status = str(row, "status");
        String stageStatus = "NO_CANDIDATE".equals(status) ? "BLOCKED" : "RECEIVED".equals(status) ? "EVALUATING" : "RESOLVED";
        Map<String, String> details = new LinkedHashMap<>();
        put(details, "capabilityCode", str(row, "capability_code"));
        put(details, "operation", str(row, "operation"));
        put(details, "selectedBindingId", str(row, "selected_binding_id"));
        if (routing != null) put(details, "governedCandidateCount", routing.details().get("candidateCount"));
        return new DecisionStage("WHO_CAN", "Who can execute", stageStatus, "CAPABILITY_PROVIDER_REGISTRY",
                str(row, "selected_binding_id"),
                "Core constructed provider candidates server-side from approved capability bindings. The historical pre-governance candidate list is not stored on this delegation, so this view does not reconstruct it from the current catalog.",
                readStrings(str(row, "reason_codes_json")), Map.copyOf(details));
    }

    private DecisionStage stage(String code, String label, DecisionEvidence evidence, DecisionStage fallback) {
        if (evidence == null) return fallback;
        return new DecisionStage(code, label, evidence.result(), evidence.authority(), evidence.evidenceId(),
                evidence.summary(), evidence.reasons(), evidence.details());
    }

    private DecisionStage whoMayFallback(Map<String, Object> row) {
        String status = str(row, "status");
        String value = "WAITING_APPROVAL".equals(status) ? "WAITING_APPROVAL" :
                ("NO_CANDIDATE".equals(status) ? "BLOCKED" : "NOT_RECORDED");
        String summary = "WAITING_APPROVAL".equals(status)
                ? "At least one candidate requires human approval. No selected WHO MAY decision exists because routing cannot start until a candidate passes governance."
                : "No selected WHO MAY evidence is attached to this delegation.";
        return new DecisionStage("WHO_MAY", "Who may execute", value, "DELEGATION_GOVERNANCE", null,
                summary, readStrings(str(row, "reason_codes_json")), Map.of());
    }

    private DecisionStage routingFallback(Map<String, Object> row) {
        String status = str(row, "status");
        return new DecisionStage("WHO_SHOULD", "Who should execute",
                "ROUTING_UNAVAILABLE".equals(status) ? "BLOCKED" : "NOT_REACHED",
                "PROVIDER_ROUTING", str(row, "routing_decision_id"),
                "Provider routing starts only after WHO MAY has produced at least one PASS candidate.",
                readStrings(str(row, "reason_codes_json")), Map.of());
    }

    private DecisionStage adapterFallback(Map<String, Object> row) {
        String status = str(row, "status");
        return new DecisionStage("HOW", "How it will execute",
                "HOW_UNAVAILABLE".equals(status) ? "BLOCKED" : "NOT_REACHED",
                "EXECUTION_ADAPTER", str(row, "adapter_resolution_id"),
                "Execution adapter resolution starts only after provider routing has selected a provider.",
                readStrings(str(row, "reason_codes_json")), Map.of());
    }

    private DecisionStage executionStage(Map<String, Object> row) {
        Map<String, String> details = map(
                "provider", str(row, "selected_provider_name"),
                "providerType", str(row, "selected_provider_type"),
                "executionKind", str(row, "execution_kind"),
                "childTaskId", str(row, "child_task_id"),
                "assignmentId", str(row, "assignment_id"),
                "dispatchRequestId", str(row, "dispatch_request_id"),
                "resultStatus", str(row, "result_status"));
        return new DecisionStage("EXECUTION", "Execution", str(row, "status"), "CANONICAL_TASK_DISPATCH_RUNTIME",
                first(str(row, "dispatch_request_id"), str(row, "child_task_id")),
                "After HOW is selected, execution converges on canonical Child Task / Assignment / Dispatch or the selected non-Agent adapter. Provider selection never bypasses canonical runtime authority.",
                readStrings(str(row, "reason_codes_json")), details);
    }

    private DecisionEvidence authorization(String tenant, String id) {
        if (blank(id)) return null;
        try {
            return jdbc.queryForObject("""
                    select decision_id,result,provider_id,provider_type,selected_policy_id,selected_policy_version,
                           approval_mode,reason_codes_json,evaluated_at
                    from delegation_authorization_decisions where tenant_id=:tenant and decision_id=:id
                    """, new MapSqlParameterSource("tenant", tenant).addValue("id", id), (rs, n) ->
                    new DecisionEvidence("DELEGATION_GOVERNANCE", rs.getString("decision_id"), rs.getString("result"),
                            "Core evaluated whether the selected capability provider is permitted for this operation.",
                            readStrings(rs.getString("reason_codes_json")), map(
                                    "providerId", rs.getString("provider_id"), "providerType", rs.getString("provider_type"),
                                    "policyId", rs.getString("selected_policy_id"), "policyVersion", String.valueOf(rs.getObject("selected_policy_version")),
                                    "approvalMode", rs.getString("approval_mode"), "evaluatedAt", String.valueOf(rs.getObject("evaluated_at")))));
        } catch (EmptyResultDataAccessException ex) { return null; }
    }

    private DecisionEvidence routing(String tenant, String id) {
        if (blank(id)) return null;
        try {
            return jdbc.queryForObject("""
                    select decision_id,result,routing_profile_id,routing_profile_version,selected_binding_id,selected_provider_id,
                           reason_codes_json,candidates_json,evaluated_at
                    from provider_routing_decisions where tenant_id=:tenant and decision_id=:id
                    """, new MapSqlParameterSource("tenant", tenant).addValue("id", id), (rs, n) -> {
                        List<Object> candidates = readList(rs.getString("candidates_json"));
                        return new DecisionEvidence("PROVIDER_ROUTING", rs.getString("decision_id"), rs.getString("result"),
                                "Core ranked only governance-approved candidates and selected the provider without selecting a transport.",
                                readStrings(rs.getString("reason_codes_json")), map(
                                        "routingProfileId", rs.getString("routing_profile_id"),
                                        "routingProfileVersion", String.valueOf(rs.getObject("routing_profile_version")),
                                        "selectedBindingId", rs.getString("selected_binding_id"),
                                        "selectedProviderId", rs.getString("selected_provider_id"),
                                        "candidateCount", String.valueOf(candidates.size()),
                                        "evaluatedAt", String.valueOf(rs.getObject("evaluated_at"))));
                    });
        } catch (EmptyResultDataAccessException ex) { return null; }
    }

    private DecisionEvidence adapter(String tenant, String id) {
        if (blank(id)) return null;
        try {
            return jdbc.queryForObject("""
                    select resolution_id,result,provider_id,provider_type,adapter_id,adapter_version,adapter_type,protocol,protocol_version,reason_codes_json,resolved_at
                    from execution_adapter_resolutions where tenant_id=:tenant and resolution_id=:id
                    """, new MapSqlParameterSource("tenant", tenant).addValue("id", id), (rs, n) ->
                    new DecisionEvidence("EXECUTION_ADAPTER", rs.getString("resolution_id"), rs.getString("result"),
                            "Core resolved HOW from the already selected provider. This evidence does not authorize or re-rank the provider.",
                            readStrings(rs.getString("reason_codes_json")), map(
                                    "providerId", rs.getString("provider_id"), "providerType", rs.getString("provider_type"),
                                    "adapterId", rs.getString("adapter_id"), "adapterVersion", String.valueOf(rs.getObject("adapter_version")),
                                    "adapterType", rs.getString("adapter_type"), "protocol", rs.getString("protocol"),
                                    "protocolVersion", rs.getString("protocol_version"), "resolvedAt", String.valueOf(rs.getObject("resolved_at")))));
        } catch (EmptyResultDataAccessException ex) { return null; }
    }

    private void bindDatabaseTenantContext(String tenant) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
    }

    private List<String> readStrings(String raw) {
        if (blank(raw)) return List.of();
        try {
            Object parsed = json.readValue(raw, Object.class);
            if (!(parsed instanceof List<?> list)) return List.of();
            return list.stream().filter(v -> v != null).map(String::valueOf).toList();
        } catch (Exception ex) { return List.of(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readMap(String raw) {
        if (blank(raw)) return Map.of();
        try {
            Object parsed = json.readValue(raw, Object.class);
            if (!(parsed instanceof Map<?, ?> source)) return Map.of();
            Map<String, String> result = new LinkedHashMap<>();
            source.forEach((k, v) -> { if (k != null && v != null) result.put(String.valueOf(k), String.valueOf(v)); });
            return Map.copyOf(result);
        } catch (Exception ex) { return Map.of(); }
    }

    private List<Object> readList(String raw) {
        if (blank(raw)) return List.of();
        try {
            Object parsed = json.readValue(raw, Object.class);
            return parsed instanceof List<?> list ? new ArrayList<>(list) : List.of();
        } catch (Exception ex) { return List.of(); }
    }

    private static Map<String, String> map(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) put(values, pairs[i], pairs[i + 1]);
        return Map.copyOf(values);
    }
    private static void put(Map<String, String> map, String key, String value) { if (!blank(value) && !"null".equals(value)) map.put(key, value); }
    private static String required(String value, String field) { if (blank(value)) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
    private static String trim(String value) { return blank(value) ? null : value.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String first(String a, String b) { return !blank(a) ? a : b; }
    private static String str(Map<String, Object> row, String key) { Object value = row.get(key); return value == null ? null : String.valueOf(value); }
    private static OffsetDateTime time(Map<String, Object> row, String key) { Object value = row.get(key); return value instanceof OffsetDateTime time ? time : null; }

    public record Page(List<ListItem> items, int total, int offset, int limit) {}
    public record ListItem(String delegationId, String parentTaskId, String childTaskId, String capabilityCode,
            String operation, String status, String selectedProviderId, String selectedProviderName,
            String selectedProviderType, String executionKind, String resultStatus,
            String resultNotificationStatus, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record Detail(ListItem summary, Map<String, String> request, Map<String, String> execution,
            List<String> reasonCodes, List<DecisionStage> stages, List<EventEvidence> events) {}
    public record DecisionStage(String code, String label, String status, String authority, String evidenceId,
            String summary, List<String> reasons, Map<String, String> details) {}
    public record EventEvidence(String eventId, String eventType, String fromStatus, String toStatus,
            List<String> reasons, Map<String, String> evidence, OffsetDateTime occurredAt) {}
    private record DecisionEvidence(String authority, String evidenceId, String result, String summary,
            List<String> reasons, Map<String, String> details) {}
}
