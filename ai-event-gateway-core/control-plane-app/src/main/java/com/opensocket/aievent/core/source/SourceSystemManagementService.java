package com.opensocket.aievent.core.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceSystemManagementService {
    private static final RowMapper<SourceSystemView> ROW_MAPPER = new SourceSystemRowMapper();

    private final NamedParameterJdbcTemplate jdbc;

    public SourceSystemManagementService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SourceSystemView> list(String tenantId) {
        String normalizedTenant = requireTenant(tenantId);
        return jdbc.query("""
                select tenant_id, source_system_id, display_name, description, status, created_at, updated_at
                  from source_systems
                 where tenant_id = :tenantId
                   and status <> 'RETIRED'
                 order by source_system_id asc
                """, new MapSqlParameterSource("tenantId", normalizedTenant), ROW_MAPPER);
    }

    public Optional<SourceSystemView> find(String tenantId, String sourceSystemId) {
        String normalizedTenant = requireTenant(tenantId);
        String normalizedSource = normalizeSourceSystemId(sourceSystemId);
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select tenant_id, source_system_id, display_name, description, status, created_at, updated_at
                      from source_systems
                     where tenant_id = :tenantId
                       and source_system_id = :sourceSystemId
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", normalizedTenant)
                    .addValue("sourceSystemId", normalizedSource), ROW_MAPPER));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Transactional
    public SourceSystemView create(String tenantId, SourceSystemView request) {
        String normalizedTenant = requireTenant(tenantId);
        String sourceSystemId = normalizeSourceSystemId(request == null ? null : request.getSourceSystemId());
        String displayName = requireNonBlank(request == null ? null : request.getDisplayName(), "displayName");
        String status = normalizeStatus(request == null ? null : request.getStatus(), "ACTIVE");
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into source_systems (
                    tenant_id, source_system_id, display_name, description, status, created_at, updated_at
                ) values (
                    :tenantId, :sourceSystemId, :displayName, :description, :status, :createdAt, :updatedAt
                )
                on conflict (tenant_id, source_system_id) do update set
                    display_name = excluded.display_name,
                    description = excluded.description,
                    status = excluded.status,
                    updated_at = excluded.updated_at
                """, new MapSqlParameterSource()
                .addValue("tenantId", normalizedTenant)
                .addValue("sourceSystemId", sourceSystemId)
                .addValue("displayName", displayName)
                .addValue("description", trimToNull(request.getDescription()))
                .addValue("status", status)
                .addValue("createdAt", now)
                .addValue("updatedAt", now));
        return find(normalizedTenant, sourceSystemId).orElseThrow();
    }

    @Transactional
    public SourceSystemView update(String tenantId, String sourceSystemId, SourceSystemView request) {
        String normalizedTenant = requireTenant(tenantId);
        String normalizedSource = normalizeSourceSystemId(sourceSystemId);
        String displayName = requireNonBlank(request == null ? null : request.getDisplayName(), "displayName");
        String status = normalizeStatus(request == null ? null : request.getStatus(), "ACTIVE");
        int updated = jdbc.update("""
                update source_systems
                   set display_name = :displayName,
                       description = :description,
                       status = :status,
                       updated_at = :updatedAt
                 where tenant_id = :tenantId
                   and source_system_id = :sourceSystemId
                """, new MapSqlParameterSource()
                .addValue("tenantId", normalizedTenant)
                .addValue("sourceSystemId", normalizedSource)
                .addValue("displayName", displayName)
                .addValue("description", trimToNull(request.getDescription()))
                .addValue("status", status)
                .addValue("updatedAt", OffsetDateTime.now()));
        if (updated == 0) {
            throw new IllegalArgumentException("Source System not found: " + normalizedSource);
        }
        return find(normalizedTenant, normalizedSource).orElseThrow();
    }

    @Transactional
    public void retire(String tenantId, String sourceSystemId) {
        String normalizedTenant = requireTenant(tenantId);
        String normalizedSource = normalizeSourceSystemId(sourceSystemId);
        jdbc.update("""
                update source_systems
                   set status = 'RETIRED',
                       updated_at = :updatedAt
                 where tenant_id = :tenantId
                   and source_system_id = :sourceSystemId
                """, new MapSqlParameterSource()
                .addValue("tenantId", normalizedTenant)
                .addValue("sourceSystemId", normalizedSource)
                .addValue("updatedAt", OffsetDateTime.now()));
    }

    private String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return tenantId.trim();
    }

    private String normalizeSourceSystemId(String sourceSystemId) {
        String value = requireNonBlank(sourceSystemId, "sourceSystemId")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_.-]", "_")
                .replaceAll("^_+|_+$", "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("sourceSystemId is required");
        }
        return value;
    }

    private String normalizeStatus(String value, String fallback) {
        String status = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "DISABLED", "RETIRED").contains(status)) {
            throw new IllegalArgumentException("Unsupported Source System status: " + status);
        }
        return status;
    }

    private String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class SourceSystemRowMapper implements RowMapper<SourceSystemView> {
        @Override
        public SourceSystemView mapRow(ResultSet rs, int rowNum) throws SQLException {
            SourceSystemView view = new SourceSystemView();
            view.setTenantId(rs.getString("tenant_id"));
            view.setSourceSystemId(rs.getString("source_system_id"));
            view.setDisplayName(rs.getString("display_name"));
            view.setDescription(rs.getString("description"));
            view.setStatus(rs.getString("status"));
            view.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
            view.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
            return view;
        }
    }
}
