package com.opensocket.aievent.database.persistence.configuration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationEmergencyOverride;
import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationGovernanceStore;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

/** PostgreSQL adapter for V40-7 emergency overlay governance. */
@DatabaseRepositoryAdapter
public class JdbcRuntimeConfigurationGovernanceStore implements RuntimeConfigurationGovernanceStore {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRuntimeConfigurationGovernanceStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RuntimeConfigurationEmergencyOverride createEmergencyOverride(
            String overrideId,
            String configSetId,
            String definitionKey,
            String baseRevisionId,
            String canonicalJson,
            String valueFingerprint,
            OffsetDateTime expiresAt,
            String actor,
            String reason,
            String correlationId) {
        jdbc.update("""
            insert into runtime_config_emergency_overrides(
              override_id,config_set_id,definition_key,base_revision_id,value_json,value_fingerprint,
              status,reason,created_by,expires_at)
            values(:id,:setId,:key,:base,cast(:value as jsonb),:fingerprint,'ACTIVE',:reason,:actor,:expiresAt)
            """, new MapSqlParameterSource()
                .addValue("id", required(overrideId, "overrideId"))
                .addValue("setId", required(configSetId, "configSetId"))
                .addValue("key", required(definitionKey, "definitionKey"))
                .addValue("base", required(baseRevisionId, "baseRevisionId"))
                .addValue("value", required(canonicalJson, "canonicalJson"))
                .addValue("fingerprint", required(valueFingerprint, "valueFingerprint"))
                .addValue("reason", required(reason, "reason"))
                .addValue("actor", required(actor, "actor"))
                .addValue("expiresAt", expiresAt));
        audit(configSetId, baseRevisionId, "EMERGENCY_OVERRIDE_CREATED", actor, reason, correlationId, overrideId);
        return findEmergencyOverride(overrideId).orElseThrow();
    }

    @Override
    public Optional<RuntimeConfigurationEmergencyOverride> findEmergencyOverride(String overrideId) {
        return jdbc.query(SELECT + " where override_id=:id",
                new MapSqlParameterSource("id", required(overrideId, "overrideId")), MAPPER)
                .stream().findFirst();
    }

    @Override
    public List<RuntimeConfigurationEmergencyOverride> listActiveEmergencyOverrides(String configSetId) {
        return jdbc.query(SELECT + " where config_set_id=:setId and status='ACTIVE' and expires_at>now() order by definition_key",
                new MapSqlParameterSource("setId", required(configSetId, "configSetId")), MAPPER);
    }

    @Override
    public RuntimeConfigurationEmergencyOverride revokeEmergencyOverride(
            String overrideId,
            String actor,
            String reason,
            String correlationId) {
        RuntimeConfigurationEmergencyOverride current = findEmergencyOverride(overrideId)
                .orElseThrow(() -> new IllegalArgumentException("Emergency override not found: " + overrideId));
        int updated = jdbc.update("""
            update runtime_config_emergency_overrides
               set status='REVOKED',revoked_by=:actor,revoked_at=now(),revoke_reason=:reason
             where override_id=:id and status='ACTIVE'
            """, new MapSqlParameterSource("id", overrideId)
                .addValue("actor", required(actor, "actor"))
                .addValue("reason", required(reason, "reason")));
        if (updated != 1) {
            throw new IllegalStateException("Emergency override is no longer ACTIVE: " + overrideId);
        }
        audit(current.configSetId(), current.baseRevisionId(), "EMERGENCY_OVERRIDE_REVOKED",
                actor, reason, correlationId, overrideId);
        return findEmergencyOverride(overrideId).orElseThrow();
    }

    @Override
    public List<RuntimeConfigurationEmergencyOverride> expireDueEmergencyOverrides(int limit, String actor) {
        List<RuntimeConfigurationEmergencyOverride> due = jdbc.query(
                SELECT + " where status='ACTIVE' and expires_at<=now() order by expires_at limit :limit",
                new MapSqlParameterSource("limit", Math.max(1, Math.min(limit, 500))), MAPPER);
        for (RuntimeConfigurationEmergencyOverride item : due) {
            int updated = jdbc.update("""
                update runtime_config_emergency_overrides
                   set status='EXPIRED'
                 where override_id=:id and status='ACTIVE'
                """, new MapSqlParameterSource("id", item.overrideId()));
            if (updated == 1) {
                audit(item.configSetId(), item.baseRevisionId(), "EMERGENCY_OVERRIDE_EXPIRED",
                        required(actor, "actor"), "Emergency override TTL expired", null, item.overrideId());
            }
        }
        return due;
    }

    private void audit(
            String configSetId,
            String revisionId,
            String action,
            String actor,
            String reason,
            String correlationId,
            String overrideId) {
        jdbc.update("""
            insert into runtime_config_audit_logs(
              audit_id,config_set_id,revision_id,action,actor,reason,correlation_id,metadata_json)
            values(:auditId,:setId,:revisionId,:action,:actor,:reason,:correlationId,cast(:metadata as jsonb))
            """, new MapSqlParameterSource("auditId", UUID.randomUUID().toString())
                .addValue("setId", configSetId)
                .addValue("revisionId", revisionId)
                .addValue("action", action)
                .addValue("actor", required(actor, "actor"))
                .addValue("reason", reason)
                .addValue("correlationId", correlationId)
                .addValue("metadata", "{\"overrideId\":\"" + jsonEscape(overrideId) + "\"}"));
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static final String SELECT = """
        select override_id,config_set_id,definition_key,base_revision_id,value_json::text,value_fingerprint,
               status,reason,created_by,created_at,expires_at,revoked_by,revoked_at,revoke_reason
          from runtime_config_emergency_overrides
        """;

    private static final RowMapper<RuntimeConfigurationEmergencyOverride> MAPPER = new RowMapper<>() {
        @Override
        public RuntimeConfigurationEmergencyOverride mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RuntimeConfigurationEmergencyOverride(
                    rs.getString("override_id"),
                    rs.getString("config_set_id"),
                    rs.getString("definition_key"),
                    rs.getString("base_revision_id"),
                    rs.getString("value_json"),
                    rs.getString("value_fingerprint"),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getString("created_by"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("expires_at", OffsetDateTime.class),
                    rs.getString("revoked_by"),
                    rs.getObject("revoked_at", OffsetDateTime.class),
                    rs.getString("revoke_reason"));
        }
    };
}
