package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL RS1 adapter for permission-neutral Resource Scope Shares. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public class JdbcResourceScopeShareRepository implements ResourceScopeShareRepositoryPort {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcResourceScopeShareRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public Optional<PersistedResourceScopeShare> findById(String tenantId, String shareId) {
        return jdbc.query("select * from resource_scope_shares where tenant_id=? and share_id=?",
                (rs,row) -> map(rs), tenantId, shareId).stream().findFirst();
    }

    @Override
    @Transactional
    public PersistedResourceScopeShare save(PersistedResourceScopeShare record, long expectedVersion) {
        Objects.requireNonNull(record, "record");
        ResourceScopeShare share = record.share();
        if (expectedVersion == 0) {
            int inserted = jdbc.update("""
                insert into resource_scope_shares(
                  tenant_id,share_id,resource_type,resource_id,target_scope_type,target_scope_id,share_reason,
                  valid_from,valid_to,share_state,idempotency_key,created_by,created_at,updated_at,revoked_by,revoked_at,version)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, share.resourceRef().tenantId(), record.shareId(), share.resourceRef().resourceType().name(),
                    share.resourceRef().resourceId(), share.target().scopeType().name(), share.target().scopeId(),
                    share.reason(), Timestamp.from(share.createdAt()), timestamp(share.expiresAt()), record.state().name(),
                    record.idempotencyKey(), share.createdBy(), Timestamp.from(share.createdAt()), Timestamp.from(record.updatedAt()),
                    blank(record.revokedBy()), timestamp(record.revokedAt()), record.version());
            if (inserted != 1) throw new IllegalStateException("RESOURCE_SCOPE_SHARE_INSERT_FAILED");
        } else {
            int updated = jdbc.update("""
                update resource_scope_shares set target_scope_type=?,target_scope_id=?,share_reason=?,valid_to=?,
                  share_state=?,updated_at=?,revoked_by=?,revoked_at=?,version=?
                where tenant_id=? and share_id=? and version=?
                """, share.target().scopeType().name(), share.target().scopeId(), share.reason(), timestamp(share.expiresAt()),
                    record.state().name(), Timestamp.from(record.updatedAt()), blank(record.revokedBy()), timestamp(record.revokedAt()),
                    record.version(), share.resourceRef().tenantId(), record.shareId(), expectedVersion);
            if (updated != 1) throw new IllegalStateException("RESOURCE_SCOPE_SHARE_VERSION_CONFLICT");
        }
        return findById(share.resourceRef().tenantId(), record.shareId())
                .orElseThrow(() -> new IllegalStateException("RESOURCE_SCOPE_SHARE_RELOAD_FAILED"));
    }

    @Override
    public Set<String> findEffectiveSharedResourceIds(String tenantId, ResourceType resourceType,
            ResourcePermissionScopeDecision permission, Instant at) {
        Objects.requireNonNull(permission, "permission");
        if (!permission.granted() || permission.tenantScoped()) return Set.of();
        List<String> match = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("resourceType", resourceType.name())
                .addValue("at", Timestamp.from(at));
        if (!permission.exactDepartmentIds().isEmpty()) {
            params.addValue("exactDepartments", permission.exactDepartmentIds());
            match.add("(s.target_scope_type='DEPARTMENT' and s.target_scope_id in (:exactDepartments))");
            match.add("(s.target_scope_type='DEPARTMENT_SUBTREE' and exists (select 1 from org_department_closure c where c.tenant_id=s.tenant_id and c.ancestor_department_id=s.target_scope_id and c.descendant_department_id in (:exactDepartments)))");
        }
        if (!permission.subtreeDepartmentRootIds().isEmpty()) {
            params.addValue("subtreeRoots", permission.subtreeDepartmentRootIds());
            match.add("(s.target_scope_type='DEPARTMENT' and exists (select 1 from org_department_closure c where c.tenant_id=s.tenant_id and c.ancestor_department_id in (:subtreeRoots) and c.descendant_department_id=s.target_scope_id))");
            match.add("(s.target_scope_type='DEPARTMENT_SUBTREE' and exists (select 1 from org_department_closure c where c.tenant_id=s.tenant_id and ((c.ancestor_department_id in (:subtreeRoots) and c.descendant_department_id=s.target_scope_id) or (c.ancestor_department_id=s.target_scope_id and c.descendant_department_id in (:subtreeRoots)))))");
        }
        if (!permission.groupIds().isEmpty()) {
            params.addValue("groups", permission.groupIds());
            match.add("(s.target_scope_type='GROUP' and s.target_scope_id in (:groups))");
        }
        if (match.isEmpty()) return Set.of();
        String sql = "select distinct s.resource_id from resource_scope_shares s where s.tenant_id=:tenantId "
                + "and s.resource_type=:resourceType and s.share_state='ACTIVE' and s.valid_from<=:at "
                + "and (s.valid_to is null or s.valid_to>:at) and (" + String.join(" or ", match) + ") order by s.resource_id";
        return new LinkedHashSet<>(named.query(sql, params, (rs,row) -> rs.getString(1)));
    }

    @Override
    public List<PersistedResourceScopeShare> findEffectiveByResource(ResourceRef resourceRef, Instant at) {
        return jdbc.query("""
            select * from resource_scope_shares
            where tenant_id=? and resource_type=? and resource_id=? and share_state='ACTIVE'
              and valid_from<=? and (valid_to is null or valid_to>?) order by share_id
            """, (rs,row) -> map(rs), resourceRef.tenantId(), resourceRef.resourceType().name(), resourceRef.resourceId(),
                Timestamp.from(at), Timestamp.from(at));
    }

    private static PersistedResourceScopeShare map(ResultSet rs) throws SQLException {
        ResourceRef ref = new ResourceRef(rs.getString("tenant_id"), ResourceType.valueOf(rs.getString("resource_type")), rs.getString("resource_id"));
        ResourceScopeShareTarget target = new ResourceScopeShareTarget(rs.getString("tenant_id"),
                CanonicalResourceScope.valueOf(rs.getString("target_scope_type")), rs.getString("target_scope_id"));
        Instant createdAt = rs.getTimestamp("valid_from").toInstant();
        ResourceScopeShare share = new ResourceScopeShare(ref, target, rs.getString("share_reason"), rs.getString("created_by"),
                createdAt, instant(rs, "valid_to"));
        return new PersistedResourceScopeShare(rs.getString("share_id"), share,
                ResourceScopeShareState.valueOf(rs.getString("share_state")), rs.getString("idempotency_key"),
                rs.getLong("version"), rs.getTimestamp("updated_at").toInstant(), nullBlank(rs.getString("revoked_by")),
                instant(rs, "revoked_at"));
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String nullBlank(String value) { return value == null ? "" : value; }
}
