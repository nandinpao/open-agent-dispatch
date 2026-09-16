package com.opensocket.aievent.core.security.incident;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fast read-side policy for Phase 12.5 containment controls.
 *
 * <p>This policy never grants authority. It can only narrow or deny an already authenticated
 * machine/task path. IAM/RBAC, Resource Access and machine boundaries remain positive authority.</p>
 */
@Service
public class RuntimeIncidentControlPolicy {
    private final NamedParameterJdbcTemplate jdbc;

    public RuntimeIncidentControlPolicy(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly=true)
    public boolean machineAuthenticationBlocked(String tenantId, String serviceAccountId, String credentialId) {
        tenant(tenantId);
        return active(tenantId, "SERVICE_ACCOUNT", serviceAccountId, List.of("SUSPEND"))
                || active(tenantId, "CREDENTIAL", credentialId, List.of("SUSPEND"));
    }

    @Transactional(readOnly=true)
    public boolean sourceSystemQuarantined(String tenantId, String sourceSystemId) {
        tenant(tenantId);
        if (blank(sourceSystemId)) return false;
        return active(tenantId, "SOURCE_SYSTEM", sourceSystemId, List.of("QUARANTINE"));
    }

    /** A Source System must still be ACTIVE in the canonical master and not be incident-quarantined. */
    @Transactional(readOnly=true)
    public boolean sourceSystemIngressAllowed(String tenantId, String sourceSystemId) {
        tenant(tenantId);
        if (blank(tenantId) || blank(sourceSystemId) || active(tenantId, "SOURCE_SYSTEM", sourceSystemId, List.of("QUARANTINE"))) return false;
        Integer count = jdbc.queryForObject("""
                select count(*) from source_systems
                 where tenant_id=:tenantId and source_system_id=:sourceSystemId and status='ACTIVE'
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("sourceSystemId", sourceSystemId), Integer.class);
        return count != null && count > 0;
    }

    @Transactional(readOnly=true)
    public boolean taskDispatchBlocked(String tenantId, String taskId) {
        tenant(tenantId);
        return active(tenantId, "TASK", taskId, List.of("HOLD", "BLOCK_RETRY"));
    }

    /** Returns the narrowest active incident limit, never a higher value than the configured base. */
    @Transactional(readOnly=true)
    public int effectiveMachineRateLimit(String tenantId, String serviceAccountId, String credentialId, int baseLimit) {
        tenant(tenantId);
        int safeBase = Math.max(1, baseLimit);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("serviceAccountId", serviceAccountId)
                .addValue("credentialId", credentialId);
        List<Integer> rows = jdbc.query("""
                select limit_per_minute
                  from security_resource_controls
                 where tenant_id=:tenantId
                   and status='ACTIVE'
                   and control_type='THROTTLE'
                   and (expires_at is null or expires_at > now())
                   and ((target_type='SERVICE_ACCOUNT' and target_id=:serviceAccountId)
                     or (target_type='CREDENTIAL' and target_id=:credentialId))
                   and limit_per_minute is not null
                """, p, (rs, rowNum) -> rs.getInt(1));
        int effective = safeBase;
        for (Integer value : rows) if (value != null && value > 0) effective = Math.min(effective, value);
        return effective;
    }

    @Transactional
    public void expireElapsedControls(String tenantId) {
        tenant(tenantId);
        jdbc.update("""
                update security_resource_controls
                   set status='EXPIRED',version=version+1
                 where tenant_id=:tenantId and status='ACTIVE' and expires_at is not null and expires_at <= now()
                """, new MapSqlParameterSource("tenantId",tenantId));
    }

    private boolean active(String tenantId, String targetType, String targetId, List<String> controlTypes) {
        if (blank(tenantId) || blank(targetId) || controlTypes == null || controlTypes.isEmpty()) return false;
        Integer count = jdbc.queryForObject("""
                select count(*)
                  from security_resource_controls
                 where tenant_id=:tenantId and target_type=:targetType and target_id=:targetId
                   and control_type in (:controlTypes) and status='ACTIVE'
                   and (expires_at is null or expires_at > now())
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("targetType", targetType)
                .addValue("targetId", targetId).addValue("controlTypes", controlTypes), Integer.class);
        return count != null && count > 0;
    }

    private void tenant(String tenantId) {
        if (blank(tenantId)) throw new IllegalArgumentException("tenantId is required");
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id',?,true)",String.class,tenantId);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
