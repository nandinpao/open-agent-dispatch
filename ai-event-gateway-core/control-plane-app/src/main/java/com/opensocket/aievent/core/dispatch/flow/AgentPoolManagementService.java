package com.opensocket.aievent.core.dispatch.flow;

import static com.opensocket.aievent.core.dispatch.flow.DispatchFlowNormalizationSupport.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedResourceSql;
import com.opensocket.aievent.core.task.TaskRepository;

/**
 * Internal Agent Pool aggregate persistence collaborator for Dispatch Flow administration.
 * C3 extracts this responsibility from the public facade without changing REST or routing authority.
 */
final class AgentPoolManagementService {
    private static final Logger log = LoggerFactory.getLogger(DispatchFlowManagementService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final DispatchFlowJsonCodec json;
    private final DispatchFlowRowMappers rowMappers;
    private final DispatchSourceOwnershipResolver sourceOwnership;

    AgentPoolManagementService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
                               DispatchSourceOwnershipResolver sourceOwnership) {
        this.jdbc = jdbc;
        this.json = new DispatchFlowJsonCodec(objectMapper);
        this.rowMappers = new DispatchFlowRowMappers(json);
        this.sourceOwnership = sourceOwnership;
    }

    List<AgentPoolView> listAgentPools(String tenantId, String sourceSystem) { return listAgentPools(tenantId, sourceSystem, null); }

    List<AgentPoolView> listAgentPools(String tenantId, String sourceSystem, ResourceListScopeQueryPlan scope) {
        String normalizedTenant = normalizeTenant(tenantId);
        String normalizedSource = normalizeNullable(sourceSystem);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", normalizedTenant);
        StringBuilder sql = new StringBuilder("""
                select
                    p.tenant_id,
                    p.pool_id,
                    p.pool_code,
                    p.pool_name,
                    p.source_system,
                    p.owner_department_id,
                    p.owner_group_id,
                    p.pool_type,
                    p.selection_strategy,
                    p.status,
                    p.description,
                    p.metadata_json,
                    p.version,
                    p.updated_by,
                    p.updated_at,
                    coalesce((
                        select count(*)::int
                          from agent_pool_members m
                         where m.tenant_id = p.tenant_id
                           and m.pool_id = p.pool_id
                           and upper(coalesce(m.member_status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                    ), 0) as member_count,
                    coalesce((
                        select count(*)::int
                          from agent_pool_members m
                          join agent_profiles ap
                            on ap.tenant_id = m.tenant_id
                           and ap.agent_id = m.agent_id
                         where m.tenant_id = p.tenant_id
                           and m.pool_id = p.pool_id
                           and upper(coalesce(m.member_status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                           and upper(coalesce(ap.approval_status, 'PENDING')) in ('APPROVED', 'ACTIVE')
                           and coalesce(ap.enabled, false) = true
                    ), 0) as available_agent_count
                  from agent_pools p
                 where p.tenant_id = :tenantId
                """);
        if (scope != null) {
            sql.append(" and ").append(ScopedResourceSql.predicate(scope,"p","pool_id","owner_department_id","owner_group_id")).append("\n");
            ScopedResourceSql.bind(params, scope);
        }
        if (!blank(normalizedSource)) {
            sql.append(" and upper(p.source_system) = :sourceSystem\n");
            params.addValue("sourceSystem", normalizedSource);
        }
        sql.append(" order by p.source_system nulls last, p.pool_type asc, p.pool_code asc");
        List<AgentPoolView> pools = jdbc.query(sql.toString(), params, rowMappers.agentPool());
        pools.forEach(pool -> pool.setMembers(agentPoolMembers(normalizedTenant, pool.getPoolId())));
        log.info("agent_pool_list_loaded tenantId={} sourceSystem={} poolCount={}", normalizedTenant, blank(normalizedSource) ? "*" : normalizedSource, pools.size());
        return pools;
    }

    Optional<AgentPoolView> findAgentPool(String tenantId, String poolId) {
        if (blank(poolId)) return Optional.empty();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalizeTenant(tenantId))
                .addValue("poolId", poolId.trim());
        try {
            AgentPoolView pool = jdbc.queryForObject("""
                    select
                        p.tenant_id,
                        p.pool_id,
                        p.pool_code,
                        p.pool_name,
                        p.source_system,
                        p.owner_department_id,
                        p.owner_group_id,
                        p.pool_type,
                        p.selection_strategy,
                        p.status,
                        p.description,
                        p.metadata_json,
                        p.version,
                        p.updated_by,
                        p.updated_at,
                        coalesce((
                            select count(*)::int
                              from agent_pool_members m
                             where m.tenant_id = p.tenant_id
                               and m.pool_id = p.pool_id
                               and upper(coalesce(m.member_status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                        ), 0) as member_count,
                        coalesce((
                            select count(*)::int
                              from agent_pool_members m
                              join agent_profiles ap
                                on ap.tenant_id = m.tenant_id
                               and ap.agent_id = m.agent_id
                             where m.tenant_id = p.tenant_id
                               and m.pool_id = p.pool_id
                               and upper(coalesce(m.member_status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                               and upper(coalesce(ap.approval_status, 'PENDING')) in ('APPROVED', 'ACTIVE')
                               and coalesce(ap.enabled, false) = true
                        ), 0) as available_agent_count
                      from agent_pools p
                     where p.tenant_id = :tenantId
                       and p.pool_id = :poolId
                    """, params, rowMappers.agentPool());
            pool.setMembers(agentPoolMembers(pool.getTenantId(), pool.getPoolId()));
            return Optional.of(pool);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    List<String> crossScopeAgentIds(String tenantId, String sourceSystem, List<AgentPoolMemberView> members) {
        String tenant = normalizeTenant(tenantId);
        String source = requireNonBlank(sourceSystem, "sourceSystem").toUpperCase(java.util.Locale.ROOT);
        Map<String,Object> owner = jdbc.query("select owner_department_id,owner_group_id from source_systems where tenant_id=:tenantId and upper(system_code)=:source and status<>'RETIRED'",
                new MapSqlParameterSource().addValue("tenantId",tenant).addValue("source",source),
                rs -> rs.next() ? Map.of("department", java.util.Objects.toString(rs.getString(1),""), "group", java.util.Objects.toString(rs.getString(2),"")) : Map.of());
        if (owner.isEmpty()) throw new IllegalArgumentException("Source System ownership is unresolved: " + sourceSystem);
        String sourceDepartment = normalizeNullable((String)owner.get("department"));
        String sourceGroup = normalizeNullable((String)owner.get("group"));
        List<String> result = new java.util.ArrayList<>();
        for (AgentPoolMemberView member : members == null ? List.<AgentPoolMemberView>of() : members) {
            if (member == null || blank(member.getAgentId())) continue;
            Map<String,Object> agent = jdbc.query("select owner_department_id,owner_group_id from agent_profiles where tenant_id=:tenantId and agent_id=:agentId and approval_status not in ('REVOKED','REJECTED')",
                    new MapSqlParameterSource().addValue("tenantId",tenant).addValue("agentId",member.getAgentId()),
                    rs -> rs.next() ? Map.of("department", java.util.Objects.toString(rs.getString(1),""), "group", java.util.Objects.toString(rs.getString(2),"")) : Map.of());
            if (agent.isEmpty()) throw new IllegalArgumentException("Governed Agent profile not found in active Tenant: " + member.getAgentId());
            String agentDepartment = normalizeNullable((String)agent.get("department"));
            String agentGroup = normalizeNullable((String)agent.get("group"));
            boolean sameDepartment = !blank(sourceDepartment) && sourceDepartment.equals(agentDepartment);
            boolean sameGroup = !blank(sourceGroup) && sourceGroup.equals(agentGroup);
            if (!sameDepartment && !sameGroup) result.add(member.getAgentId());
        }
        return List.copyOf(result);
    }

    void retireAgentPool(String tenantId, String poolId) {
        jdbc.update("""
                update agent_pools
                   set status = 'RETIRED', updated_at = now()
                 where tenant_id = :tenantId and pool_id = :poolId
                """, new MapSqlParameterSource()
                .addValue("tenantId", normalizeTenant(tenantId))
                .addValue("poolId", requireNonBlank(poolId, "poolId")));
    }

    private void acquirePoolLock(String tenantId, String poolId) {
        jdbc.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(:aggregateKey, 0))",
                new MapSqlParameterSource().addValue("aggregateKey", "agent-pool:" + tenantId + ":" + poolId));
    }

    private void acquirePoolCodeLock(String tenantId, String poolCode) {
        jdbc.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(:aggregateKey, 0))",
                new MapSqlParameterSource().addValue("aggregateKey", "agent-pool-code:" + tenantId + ":" + normalizeCode(poolCode)));
    }

    private Optional<AgentPoolView> findAgentPoolByCode(String tenantId, String poolCode) {
        if (blank(poolCode)) return Optional.empty();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalizeTenant(tenantId))
                .addValue("poolCode", normalizeCode(poolCode));
        List<String> ids = jdbc.queryForList("""
                select pool_id
                  from agent_pools
                 where tenant_id = :tenantId
                   and upper(pool_code) = :poolCode
                 limit 1
                """, params, String.class);
        if (ids.isEmpty()) return Optional.empty();
        return findAgentPool(tenantId, ids.get(0));
    }

    private List<AgentPoolMemberView> agentPoolMembers(String tenantId, String poolId) {
        return jdbc.query("""
                select
                    m.tenant_id,
                    m.pool_id,
                    p.pool_code,
                    m.agent_id,
                    coalesce(nullif(ap.agent_name, ''), m.agent_id) as agent_name,
                    m.member_status,
                    m.priority,
                    m.weight,
                    upper(coalesce(ap.approval_status, 'PENDING')) as approval_status,
                    coalesce((
                        select upper(coalesce(a.status, 'UNKNOWN'))
                          from agents a
                         where a.agent_id = m.agent_id
                         order by a.last_heartbeat_at desc nulls last, a.updated_at desc nulls last
                         limit 1
                    ), 'UNKNOWN') as runtime_status,
                    m.metadata_json,
                    m.version,
                    m.updated_by,
                    m.updated_at
                  from agent_pool_members m
                  join agent_pools p
                    on p.tenant_id = m.tenant_id
                   and p.pool_id = m.pool_id
                  left join agent_profiles ap
                    on ap.tenant_id = m.tenant_id
                   and ap.agent_id = m.agent_id
                 where m.tenant_id = :tenantId
                   and m.pool_id = :poolId
                 order by m.priority asc, m.weight desc, m.agent_id asc
                """, new MapSqlParameterSource()
                .addValue("tenantId", normalizeTenant(tenantId))
                .addValue("poolId", requireNonBlank(poolId, "poolId")), rowMappers.agentPoolMember());
    }

    private AgentPoolView normalizeAgentPool(AgentPoolView request) {
        AgentPoolView pool = request == null ? new AgentPoolView() : request;
        String tenantId = normalizeTenant(pool.getTenantId());
        String source = normalizeNullable(pool.getSourceSystem());
        String poolCode = normalizeCode(firstNonBlank(pool.getPoolCode(), source == null ? null : source + "_TRIAGE_POOL", pool.getPoolName()));
        String poolId = firstNonBlank(pool.getPoolId(), "pool-" + safeIdPart(tenantId) + "-" + safeIdPart(poolCode));
        pool.setTenantId(tenantId);
        pool.setPoolId(poolId);
        pool.setPoolCode(requireNonBlank(poolCode, "poolCode"));
        pool.setPoolName(firstNonBlank(pool.getPoolName(), pool.getPoolCode().replace('_', ' ')));
        pool.setSourceSystem(source);
        sourceOwnership.applyToAgentPool(tenantId, source, pool);
        pool.setPoolType(normalizeCode(firstNonBlank(pool.getPoolType(), "RESOLUTION")));
        pool.setSelectionStrategy(normalizeSupportedPoolSelectionStrategy(pool.getSelectionStrategy()));
        pool.setStatus(normalizeCode(firstNonBlank(pool.getStatus(), "ACTIVE")));
        List<AgentPoolMemberView> members = new ArrayList<>();
        for (AgentPoolMemberView rawMember : pool.getMembers()) {
            if (rawMember == null || blank(rawMember.getAgentId())) continue;
            AgentPoolMemberView member = new AgentPoolMemberView();
            member.setTenantId(tenantId);
            member.setPoolId(poolId);
            member.setPoolCode(pool.getPoolCode());
            member.setAgentId(rawMember.getAgentId().trim());
            member.setAgentName(rawMember.getAgentName());
            member.setMemberStatus(normalizeCode(firstNonBlank(rawMember.getMemberStatus(), "ACTIVE")));
            member.setPriority(rawMember.getPriority() == null ? 100 : rawMember.getPriority());
            member.setWeight(rawMember.getWeight() == null ? 1 : rawMember.getWeight());
            member.setApprovalStatus(rawMember.getApprovalStatus());
            member.setRuntimeStatus(rawMember.getRuntimeStatus());
            member.setMetadata(rawMember.getMetadata());
            members.add(member);
        }
        pool.setMembers(members);
        return pool;
    }

    private void validateAgentPoolMembers(AgentPoolView pool) {
        requireUnique(pool.getMembers().stream().map(AgentPoolMemberView::getAgentId).toList(), "agentPoolMemberAgentId");
        if (pool.getMembers().isEmpty()) return;
        List<String> agentIds = pool.getMembers().stream().map(AgentPoolMemberView::getAgentId).distinct().toList();
        List<String> existing = jdbc.queryForList("""
                select agent_id
                  from agent_profiles
                 where tenant_id = :tenantId
                   and agent_id in (:agentIds)
                """, new MapSqlParameterSource()
                .addValue("tenantId", pool.getTenantId())
                .addValue("agentIds", agentIds), String.class);
        Set<String> missing = new LinkedHashSet<>(agentIds);
        missing.removeAll(existing);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Agent Pool members do not exist in the selected tenant: " + String.join(", ", missing));
        }
    }

    private void writeAgentPoolMember(AgentPoolView pool, AgentPoolMemberView member) {
        jdbc.update("""
                insert into agent_pool_members (
                    tenant_id, pool_id, agent_id, member_status, priority, weight, metadata_json, created_at, updated_at
                ) values (
                    :tenantId, :poolId, :agentId, :memberStatus, :priority, :weight, cast(:metadataJson as jsonb), now(), now()
                )
                on conflict (tenant_id, pool_id, agent_id) do update set
                    member_status = excluded.member_status,
                    priority = excluded.priority,
                    weight = excluded.weight,
                    metadata_json = excluded.metadata_json,
                    updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("tenantId", pool.getTenantId())
                .addValue("poolId", pool.getPoolId())
                .addValue("agentId", member.getAgentId())
                .addValue("memberStatus", member.getMemberStatus())
                .addValue("priority", member.getPriority())
                .addValue("weight", member.getWeight())
                .addValue("metadataJson", json.write(member.getMetadata())));
    }

    AgentPoolView createOrUpdateAgentPool(AgentPoolView request, TaskRepository taskRepository) {
        return createOrUpdateAgentPool(request, request == null ? null : request.getVersion(), taskRepository);
    }

    AgentPoolView createOrUpdateAgentPool(AgentPoolView request, Integer expectedVersion, TaskRepository taskRepository) {
        AgentPoolView normalized = normalizeAgentPool(request);
        // pool_code is a tenant-scoped alternate key. Lock both identities before checking so
        // two concurrent creates cannot race through the preflight and hit the database unique key.
        acquirePoolCodeLock(normalized.getTenantId(), normalized.getPoolCode());
        acquirePoolLock(normalized.getTenantId(), normalized.getPoolId());
        AgentPoolView existing = findAgentPool(normalized.getTenantId(), normalized.getPoolId()).orElse(null);
        AgentPoolView existingByCode = findAgentPoolByCode(normalized.getTenantId(), normalized.getPoolCode()).orElse(null);
        if (existingByCode != null && !existingByCode.getPoolId().equals(normalized.getPoolId())) {
            throw new StandardApiException("AGENT_POOL_CODE_CONFLICT",
                    "Agent Pool code already exists in this Workspace: " + normalized.getPoolCode()
                            + ". Open the existing Agent Pool and edit it instead of creating a duplicate. existingPoolId="
                            + existingByCode.getPoolId());
        }
        assertExpectedVersion("Agent Pool", normalized.getPoolId(), expectedVersion, existing == null ? null : existing.getVersion());
        validateAgentPoolMembers(normalized);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalized.getTenantId())
                .addValue("poolId", normalized.getPoolId())
                .addValue("poolCode", normalized.getPoolCode())
                .addValue("poolName", normalized.getPoolName())
                .addValue("sourceSystem", normalizeNullable(normalized.getSourceSystem()))
                .addValue("ownerDepartmentId", normalized.getOwnerDepartmentId())
                .addValue("ownerGroupId", normalized.getOwnerGroupId())
                .addValue("poolType", normalized.getPoolType())
                .addValue("selectionStrategy", normalized.getSelectionStrategy())
                .addValue("status", normalized.getStatus())
                .addValue("description", normalized.getDescription())
                .addValue("metadataJson", json.write(normalized.getMetadata()))
                .addValue("expectedVersion", expectedVersion);
        int poolRows = jdbc.update("""
                insert into agent_pools (
                    tenant_id, pool_id, pool_code, pool_name, source_system, owner_department_id, owner_group_id,
                    pool_type, selection_strategy, status, description, metadata_json, created_at, updated_at
                ) values (
                    :tenantId, :poolId, :poolCode, :poolName, :sourceSystem, :ownerDepartmentId, :ownerGroupId,
                    :poolType, :selectionStrategy, :status, :description, cast(:metadataJson as jsonb), now(), now()
                )
                on conflict (tenant_id, pool_id) do update set
                    pool_code = excluded.pool_code,
                    pool_name = excluded.pool_name,
                    source_system = excluded.source_system,
                    owner_department_id = excluded.owner_department_id,
                    owner_group_id = excluded.owner_group_id,
                    pool_type = excluded.pool_type,
                    selection_strategy = excluded.selection_strategy,
                    status = excluded.status,
                    description = excluded.description,
                    metadata_json = excluded.metadata_json,
                    updated_at = now()
                where agent_pools.version = :expectedVersion
                """, params);
        if (existing != null && poolRows == 0) {
            throwVersionConflict("Agent Pool", normalized.getPoolId(), expectedVersion, existing.getVersion());
        }
        jdbc.update("delete from agent_pool_members where tenant_id = :tenantId and pool_id = :poolId", params);
        for (AgentPoolMemberView member : normalized.getMembers()) {
            writeAgentPoolMember(normalized, member);
        }
        int awakened = taskRepository == null ? 0 : taskRepository.wakeConfigurationBlockedTasks(
                normalized.getTenantId(), normalized.getSourceSystem(), OffsetDateTime.now(),
                "Agent Pool configuration changed: " + normalized.getPoolCode());
        log.info("agent_pool_saved tenantId={} poolId={} poolCode={} sourceSystem={} memberCount={} awakenedTasks={}",
                normalized.getTenantId(), normalized.getPoolId(), normalized.getPoolCode(), normalized.getSourceSystem(),
                normalized.getMembers().size(), awakened);
        return findAgentPool(normalized.getTenantId(), normalized.getPoolId())
                .orElseThrow(() -> new IllegalStateException("Agent Pool was not readable after save: " + normalized.getPoolId()));
    }

    private void assertExpectedVersion(String entityType, String entityId, Integer expectedVersion, Integer currentVersion) {
        if (currentVersion == null) {
            return;
        }
        if (expectedVersion == null) {
            throw new StandardApiException(StandardApiErrorCode.RESOURCE_VERSION_CONFLICT,
                    entityType + " requires expectedVersion / If-Match before update: " + entityId);
        }
        if (!expectedVersion.equals(currentVersion)) {
            throwVersionConflict(entityType, entityId, expectedVersion, currentVersion);
        }
    }

    private void throwVersionConflict(String entityType, String entityId, Integer expectedVersion, Integer currentVersion) {
        throw new StandardApiException(StandardApiErrorCode.RESOURCE_VERSION_CONFLICT,
                entityType + " was updated by another administrator. Reload before saving again. entityId=" + entityId
                        + ", expectedVersion=" + expectedVersion + ", currentVersion=" + currentVersion);
    }

    private void requireUnique(List<String> values, String fieldName) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate " + fieldName + " in Agent Pool aggregate: " + value);
            }
        }
    }
}
