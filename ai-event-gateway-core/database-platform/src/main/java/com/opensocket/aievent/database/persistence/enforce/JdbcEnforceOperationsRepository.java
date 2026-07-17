package com.opensocket.aievent.database.persistence.enforce;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.enforce.EnforceArtifactRetentionRecord;
import com.opensocket.aievent.core.enforce.EnforceLegacyFinalReportItem;
import com.opensocket.aievent.core.enforce.EnforceObservabilitySnapshot;
import com.opensocket.aievent.core.enforce.EnforceOperationsRepository;
import com.opensocket.aievent.core.enforce.EnforceOperatorIncidentRequest;
import com.opensocket.aievent.core.enforce.EnforceOperatorIncidentResult;
import com.opensocket.aievent.core.enforce.EnforceRoutingAuditRecord;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class JdbcEnforceOperationsRepository implements EnforceOperationsRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper objectMapper;

    public JdbcEnforceOperationsRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.objectMapper = objectMapper;
    }

    @Override
    public EnforceObservabilitySnapshot observabilitySnapshot() {
        return jdbc.queryForObject("""
                select generated_at,source,mode,metric_window,v2_allowed,v2_blocked,no_candidate,
                       fallback_denied,quality_unavailable,score_breakdown_missing,blocked_rate,
                       no_candidate_rate,quality_unavailable_rate,readiness_blocking_count
                  from dispatch_p11_enforce_observability_snapshot
                """, (rs, rowNum) -> new EnforceObservabilitySnapshot(
                offset(rs, "generated_at"), rs.getString("source"), rs.getString("mode"),
                rs.getString("metric_window"), rs.getLong("v2_allowed"), rs.getLong("v2_blocked"),
                rs.getLong("no_candidate"), rs.getLong("fallback_denied"),
                rs.getLong("quality_unavailable"), rs.getLong("score_breakdown_missing"),
                rs.getDouble("blocked_rate"), rs.getDouble("no_candidate_rate"),
                rs.getDouble("quality_unavailable_rate"), latestArtifact("ACCEPTANCE"),
                latestArtifact("READINESS"), latestArtifact("ARCHIVE_MANIFEST"),
                rs.getLong("readiness_blocking_count")));
    }

    @Override
    public List<EnforceRoutingAuditRecord> searchRoutingAudit(String taskId, String agentId,
            String blockingCode, String policyCode, String window, int limit) {
        StringBuilder sql = new StringBuilder("""
                select decision_id,task_id,agent_id,policy_code,blocking_code,eligibility_engine_mode,
                       eligibility_v2_applied,eligibility_v2_candidate_eligible,eligibility_v2_score,
                       eligibility_v2_blocking_reasons,eligibility_v2_score_breakdown,created_at
                  from dispatch_p11_routing_audit
                 where created_at >= now() - cast(:window as interval)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("window", normalizeWindow(window))
                .addValue("limit", Math.max(1, Math.min(limit, 500)));
        if (taskId != null) { sql.append(" and task_id=:taskId"); params.addValue("taskId", taskId); }
        if (agentId != null) { sql.append(" and agent_id=:agentId"); params.addValue("agentId", agentId); }
        if (blockingCode != null) { sql.append(" and upper(coalesce(blocking_code,''))=:blockingCode"); params.addValue("blockingCode", blockingCode); }
        if (policyCode != null) { sql.append(" and upper(coalesce(policy_code,''))=:policyCode"); params.addValue("policyCode", policyCode); }
        sql.append(" order by created_at desc limit :limit");
        return named.query(sql.toString(), params, (rs, rowNum) -> new EnforceRoutingAuditRecord(
                rs.getString("decision_id"), rs.getString("task_id"), rs.getString("agent_id"),
                rs.getString("policy_code"), rs.getString("blocking_code"),
                rs.getString("eligibility_engine_mode"), rs.getBoolean("eligibility_v2_applied"),
                rs.getBoolean("eligibility_v2_candidate_eligible"), integer(rs, "eligibility_v2_score"),
                stringList(rs.getString("eligibility_v2_blocking_reasons")),
                scoreBreakdown(rs.getString("eligibility_v2_score_breakdown")), offset(rs, "created_at")));
    }

    @Override
    public EnforceOperatorIncidentResult createOperatorIncident(EnforceOperatorIncidentRequest request, String actor) {
        String id = "dispatch-operator-incident-" + UUID.randomUUID();
        jdbc.update("""
                insert into dispatch_operator_incidents(
                    incident_id,trigger_code,severity,task_id,agent_id,message,metadata_json,status,
                    created_by,updated_by)
                values(?,?,?,?,?,?,cast(? as jsonb),'OPEN',?,?)
                """, id, request.getTriggerCode(), request.getSeverity(), request.getTaskId(), request.getAgentId(),
                request.getMessage(), json(request.getMetadata()), actor, actor);
        return new EnforceOperatorIncidentResult(id, null, "OPEN", "Operator incident created");
    }

    @Override
    public List<EnforceLegacyFinalReportItem> legacyFinalReport() {
        return jdbc.query("""
                select category,count,severity,sample_refs
                  from dispatch_p11_legacy_final_report
                 order by severity desc,category
                """, (rs, rowNum) -> new EnforceLegacyFinalReportItem(
                rs.getString("category"), rs.getLong("count"), rs.getString("severity"),
                sqlArray(rs.getArray("sample_refs"))));
    }

    @Override
    public List<EnforceArtifactRetentionRecord> artifactRetention() {
        return jdbc.query("""
                select artifact_name,artifact_path,generated_at,retained_until,source
                  from dispatch_p11_artifact_retention
                 order by generated_at desc
                 limit 500
                """, (rs, rowNum) -> new EnforceArtifactRetentionRecord(
                rs.getString("artifact_name"), rs.getString("artifact_path"),
                offset(rs, "generated_at"), offset(rs, "retained_until"), rs.getString("source")));
    }

    private String latestArtifact(String source) {
        List<String> values = jdbc.query("""
                select artifact_path from dispatch_release_artifacts
                 where source=? order by generated_at desc limit 1
                """, (rs, rowNum) -> rs.getString(1), source);
        return values.isEmpty() ? null : values.getFirst();
    }

    private Map<String, Object> scoreBreakdown(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, value) -> result.put(String.valueOf(key), value));
                return Map.copyOf(result);
            }
            return Map.of("candidates", parsed);
        } catch (Exception ex) {
            return Map.of("raw", raw);
        }
    }

    private List<String> stringList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof List<?> list) return list.stream().map(String::valueOf).toList();
            if (parsed instanceof Map<?, ?> map) return map.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue()).toList();
            return List.of(String.valueOf(parsed));
        } catch (Exception ex) {
            return List.of(raw);
        }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception ex) { throw new IllegalArgumentException("metadata cannot be serialized", ex); }
    }

    private static String normalizeWindow(String window) {
        if (window == null || window.isBlank()) return "24 hours";
        String value = window.trim().toLowerCase();
        if (value.matches("[1-9][0-9]*h")) return value.substring(0, value.length() - 1) + " hours";
        if (value.matches("[1-9][0-9]*d")) return value.substring(0, value.length() - 1) + " days";
        return "24 hours";
    }

    private static OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static List<String> sqlArray(Array array) throws SQLException {
        if (array == null) return List.of();
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object value : values) if (value != null) result.add(String.valueOf(value));
        return List.copyOf(result);
    }
}
