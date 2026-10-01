package com.opensocket.aievent.database.persistence.configuration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinition;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinitionStore;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

/** V40-8.1 read adapter for the source-definition materialization table. */
@DatabaseRepositoryAdapter
public class JdbcRuntimeConfigurationDefinitionStore implements RuntimeConfigurationDefinitionStore {
    private static final String SELECT = """
        select definition_key,display_name,owner,domain_owner,authority_class,scope,scope_ref,data_type,unit,risk,
               mutability,consumer_contract,config_set_key,requires_approval,admin_editable,validation_rule::text,
               initial_seed::text,introduced_version,ui_metadata::text,review_status,migration_authorized,
               schema_version,source_ref
          from runtime_config_definitions
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRuntimeConfigurationDefinitionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RuntimeConfigurationDefinition> listAll() {
        return jdbc.query(SELECT + " order by definition_key", new MapSqlParameterSource(), MAPPER);
    }

    @Override
    public List<RuntimeConfigurationDefinition> listMigrationAuthorized() {
        return jdbc.query(SELECT + " where migration_authorized=true order by definition_key", new MapSqlParameterSource(), MAPPER);
    }

    @Override
    public Optional<RuntimeConfigurationDefinition> findByKey(String key) {
        return jdbc.query(SELECT + " where definition_key=:key", new MapSqlParameterSource("key", required(key)), MAPPER)
            .stream().findFirst();
    }

    private static final RowMapper<RuntimeConfigurationDefinition> MAPPER = (rs, row) -> map(rs);

    private static RuntimeConfigurationDefinition map(ResultSet rs) throws SQLException {
        return new RuntimeConfigurationDefinition(
            rs.getString("definition_key"), rs.getString("display_name"), rs.getString("owner"), rs.getString("domain_owner"),
            rs.getString("authority_class"), rs.getString("scope"), rs.getString("scope_ref"), rs.getString("data_type"),
            rs.getString("unit"), rs.getString("risk"), rs.getString("mutability"), rs.getString("consumer_contract"),
            rs.getString("config_set_key"), rs.getBoolean("requires_approval"), rs.getBoolean("admin_editable"),
            rs.getString("validation_rule"), rs.getString("initial_seed"), rs.getString("introduced_version"),
            rs.getString("ui_metadata"), rs.getString("review_status"), rs.getBoolean("migration_authorized"),
            rs.getInt("schema_version"), rs.getString("source_ref"));
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("definition key is required");
        return value.trim();
    }
}
