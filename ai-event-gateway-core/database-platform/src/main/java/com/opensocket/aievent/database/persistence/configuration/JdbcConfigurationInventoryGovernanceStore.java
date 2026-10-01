package com.opensocket.aievent.database.persistence.configuration;

import com.opensocket.aievent.core.kernel.configuration.ConfigurationInventoryGovernanceStatus;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernance;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernanceEvent;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryObservation;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** V40-9A persistence adapter for source-bound migration governance. */
@DatabaseRepositoryAdapter
public class JdbcConfigurationInventoryGovernanceStore implements ConfigurationInventoryGovernanceStore {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcConfigurationInventoryGovernanceStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ConfigurationInventoryObservation> listObservations(ConfigurationInventoryGovernanceStatus status, String namespace, String query, int limit) {
        StringBuilder sql = new StringBuilder("""
            select o.configuration_key,o.namespace,o.source_observation_hash,o.current_files::text,o.profiles::text,
                   o.value_shapes::text,o.review_flags::text,o.mutability_floor,o.source_usage_evidence::text,
                   o.advisory_classification::text,o.observed_at
              from runtime_config_inventory_observations o
              join runtime_config_inventory_governance g on g.configuration_key=o.configuration_key
             where 1=1
            """);
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (status != null) { sql.append(" and g.status=:status"); p.addValue("status", status.name()); }
        if (namespace != null && !namespace.isBlank()) { sql.append(" and o.namespace=:namespace"); p.addValue("namespace", namespace.trim()); }
        if (query != null && !query.isBlank()) { sql.append(" and lower(o.configuration_key) like :query"); p.addValue("query", "%" + query.trim().toLowerCase() + "%"); }
        sql.append(" order by o.namespace,o.configuration_key limit :limit");
        p.addValue("limit", Math.max(1, Math.min(limit, 1000)));
        return jdbc.query(sql.toString(), p, OBSERVATION_MAPPER);
    }

    @Override
    public Optional<ConfigurationInventoryObservation> findObservation(String key) {
        return jdbc.query("""
            select configuration_key,namespace,source_observation_hash,current_files::text,profiles::text,value_shapes::text,
                   review_flags::text,mutability_floor,source_usage_evidence::text,advisory_classification::text,observed_at
              from runtime_config_inventory_observations where configuration_key=:key
            """, new MapSqlParameterSource("key", required(key)), OBSERVATION_MAPPER).stream().findFirst();
    }

    @Override
    public Optional<ConfigurationInventoryGovernance> findGovernance(String key) {
        return jdbc.query("""
            select configuration_key,status,source_observation_hash,domain_owner,authority_class,scope,risk,mutability,
                   consumer_contract,admin_editable,requires_approval,classified_by,classified_at,owner_reviewed_by,
                   owner_reviewed_at,architecture_approved_by,architecture_approved_at,migration_authorized_by,
                   migration_authorized_at,reason,version,updated_at
              from runtime_config_inventory_governance where configuration_key=:key
            """, new MapSqlParameterSource("key", required(key)), GOVERNANCE_MAPPER).stream().findFirst();
    }

    @Override
    public List<ConfigurationInventoryGovernanceEvent> listEvents(String key, int limit) {
        return jdbc.query("""
            select event_id,configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,
                   detail_json::text,created_at
              from runtime_config_inventory_governance_events
             where configuration_key=:key order by event_id desc limit :limit
            """, new MapSqlParameterSource().addValue("key", required(key)).addValue("limit", Math.max(1, Math.min(limit, 200))), EVENT_MAPPER);
    }

    @Override
    @Transactional
    public ConfigurationInventoryGovernance save(ConfigurationInventoryGovernance next, long expectedVersion, String eventType, String actor, String reason, String detailJson) {
        ConfigurationInventoryGovernance before = findGovernance(next.configurationKey())
            .orElseThrow(() -> new IllegalArgumentException("Configuration inventory governance not found: " + next.configurationKey()));
        int updated = jdbc.update("""
            update runtime_config_inventory_governance set
              status=:status,source_observation_hash=:sourceHash,domain_owner=:domainOwner,authority_class=:authorityClass,
              scope=:scope,risk=:risk,mutability=:mutability,consumer_contract=:consumerContract,
              admin_editable=:adminEditable,requires_approval=:requiresApproval,classified_by=:classifiedBy,
              classified_at=:classifiedAt,owner_reviewed_by=:ownerReviewedBy,owner_reviewed_at=:ownerReviewedAt,
              architecture_approved_by=:architectureApprovedBy,architecture_approved_at=:architectureApprovedAt,
              migration_authorized_by=:migrationAuthorizedBy,migration_authorized_at=:migrationAuthorizedAt,
              reason=:reason,version=version+1,updated_at=now()
             where configuration_key=:key and version=:expectedVersion
            """, params(next).addValue("expectedVersion", expectedVersion));
        if (updated != 1) throw new IllegalStateException("CONFIGURATION_GOVERNANCE_VERSION_CONFLICT");
        jdbc.update("""
            insert into runtime_config_inventory_governance_events(
              configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
            values(:key,:eventType,:fromStatus,:toStatus,:actor,:eventReason,:sourceHash,cast(:detailJson as jsonb))
            """, new MapSqlParameterSource()
                .addValue("key", next.configurationKey()).addValue("eventType", required(eventType))
                .addValue("fromStatus", before.status().name()).addValue("toStatus", next.status().name())
                .addValue("actor", required(actor)).addValue("eventReason", required(reason))
                .addValue("sourceHash", next.sourceObservationHash()).addValue("detailJson", detailJson == null ? "{}" : detailJson));
        return findGovernance(next.configurationKey()).orElseThrow();
    }

    private static MapSqlParameterSource params(ConfigurationInventoryGovernance g) {
        return new MapSqlParameterSource()
            .addValue("key", g.configurationKey()).addValue("status", g.status().name())
            .addValue("sourceHash", g.sourceObservationHash()).addValue("domainOwner", g.domainOwner())
            .addValue("authorityClass", g.authorityClass()).addValue("scope", g.scope()).addValue("risk", g.risk())
            .addValue("mutability", g.mutability()).addValue("consumerContract", g.consumerContract())
            .addValue("adminEditable", g.adminEditable()).addValue("requiresApproval", g.requiresApproval())
            .addValue("classifiedBy", g.classifiedBy()).addValue("classifiedAt", g.classifiedAt())
            .addValue("ownerReviewedBy", g.ownerReviewedBy()).addValue("ownerReviewedAt", g.ownerReviewedAt())
            .addValue("architectureApprovedBy", g.architectureApprovedBy()).addValue("architectureApprovedAt", g.architectureApprovedAt())
            .addValue("migrationAuthorizedBy", g.migrationAuthorizedBy()).addValue("migrationAuthorizedAt", g.migrationAuthorizedAt())
            .addValue("reason", g.reason());
    }

    private static final RowMapper<ConfigurationInventoryObservation> OBSERVATION_MAPPER = (rs, row) -> new ConfigurationInventoryObservation(
        rs.getString("configuration_key"), rs.getString("namespace"), rs.getString("source_observation_hash"), rs.getString("current_files"),
        rs.getString("profiles"), rs.getString("value_shapes"), rs.getString("review_flags"), rs.getString("mutability_floor"),
        rs.getString("source_usage_evidence"), rs.getString("advisory_classification"), rs.getObject("observed_at", java.time.OffsetDateTime.class));

    private static final RowMapper<ConfigurationInventoryGovernance> GOVERNANCE_MAPPER = (rs, row) -> new ConfigurationInventoryGovernance(
        rs.getString("configuration_key"), ConfigurationInventoryGovernanceStatus.valueOf(rs.getString("status")), rs.getString("source_observation_hash"),
        rs.getString("domain_owner"), rs.getString("authority_class"), rs.getString("scope"), rs.getString("risk"), rs.getString("mutability"),
        rs.getString("consumer_contract"), nullableBoolean(rs,"admin_editable"), nullableBoolean(rs,"requires_approval"), rs.getString("classified_by"),
        rs.getObject("classified_at", java.time.OffsetDateTime.class), rs.getString("owner_reviewed_by"), rs.getObject("owner_reviewed_at", java.time.OffsetDateTime.class),
        rs.getString("architecture_approved_by"), rs.getObject("architecture_approved_at", java.time.OffsetDateTime.class), rs.getString("migration_authorized_by"),
        rs.getObject("migration_authorized_at", java.time.OffsetDateTime.class), rs.getString("reason"), rs.getLong("version"), rs.getObject("updated_at", java.time.OffsetDateTime.class));

    private static final RowMapper<ConfigurationInventoryGovernanceEvent> EVENT_MAPPER = (rs, row) -> new ConfigurationInventoryGovernanceEvent(
        rs.getLong("event_id"), rs.getString("configuration_key"), rs.getString("event_type"), rs.getString("from_status"), rs.getString("to_status"),
        rs.getString("actor"), rs.getString("reason"), rs.getString("source_observation_hash"), rs.getString("detail_json"),
        rs.getObject("created_at", java.time.OffsetDateTime.class));

    private static Boolean nullableBoolean(ResultSet rs, String name) throws SQLException {
        boolean value = rs.getBoolean(name); return rs.wasNull() ? null : value;
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("required value is missing");
        return value.trim();
    }
}
