package com.opensocket.aievent.database.persistence.dispatch.flow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingMember;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingRepository;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingSnapshot;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class JdbcAgentPoolRoutingRepository implements AgentPoolRoutingRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAgentPoolRoutingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AgentPoolRoutingSnapshot> findActivePool(String tenantId, String poolId) {
        if (blank(tenantId) || blank(poolId)) {
            return Optional.empty();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantIds", tenantAliases(tenantId));
        params.put("poolId", poolId.trim());
        params.put("normalizedPoolId", normalizePoolId(poolId));
        try {
            List<AgentPoolRoutingRow> rows = jdbc.query(SQL, new MapSqlParameterSource(params), this::mapRow);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            AgentPoolRoutingRow first = rows.getFirst();
            AgentPoolRoutingSnapshot snapshot = new AgentPoolRoutingSnapshot();
            snapshot.setTenantId(first.tenantId());
            snapshot.setPoolId(first.poolId());
            snapshot.setPoolCode(first.poolCode());
            snapshot.setPoolName(first.poolName());
            snapshot.setSourceSystem(first.sourceSystem());
            snapshot.setPoolType(first.poolType());
            snapshot.setSelectionStrategy(first.selectionStrategy());
            snapshot.setStatus(first.status());
            snapshot.setMembers(rows.stream()
                    .filter(row -> !blank(row.agentId()))
                    .map(row -> {
                        AgentPoolRoutingMember member = new AgentPoolRoutingMember();
                        member.setTenantId(row.tenantId());
                        member.setPoolId(row.poolId());
                        member.setAgentId(row.agentId());
                        member.setMemberStatus(row.memberStatus());
                        member.setPriority(row.priority());
                        member.setWeight(row.weight());
                        return member;
                    })
                    .toList());
            return Optional.of(snapshot);
        } catch (DataAccessException ex) {
            throw ex;
        }
    }

    private static final String SQL = """
            select
                p.tenant_id,
                p.pool_id,
                p.pool_code,
                p.pool_name,
                p.source_system,
                p.pool_type,
                p.selection_strategy,
                p.status,
                m.agent_id,
                m.member_status,
                m.priority,
                m.weight
            from agent_pools p
            left join agent_pool_members m
              on m.tenant_id = p.tenant_id
             and m.pool_id = p.pool_id
             and upper(coalesce(m.member_status, 'ACTIVE')) in ('ACTIVE','ENABLED','APPROVED')
            where upper(p.tenant_id) in (:tenantIds)
              and (p.pool_id = :poolId or upper(replace(replace(replace(p.pool_id, '-', '_'), '.', '_'), ' ', '_')) = :normalizedPoolId)
              and upper(coalesce(p.status, 'ACTIVE')) in ('ACTIVE','ENABLED')
            order by m.priority asc nulls last, m.weight desc nulls last, m.updated_at asc nulls last, m.agent_id asc
            """;

    private AgentPoolRoutingRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AgentPoolRoutingRow(
                rs.getString("tenant_id"),
                rs.getString("pool_id"),
                rs.getString("pool_code"),
                rs.getString("pool_name"),
                rs.getString("source_system"),
                rs.getString("pool_type"),
                rs.getString("selection_strategy"),
                rs.getString("status"),
                rs.getString("agent_id"),
                rs.getString("member_status"),
                rs.getInt("priority"),
                rs.getInt("weight"));
    }

    private static String normalizePoolId(String value) {
        if (blank(value)) return null;
        return value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static List<String> tenantAliases(String rawTenantId) {
        if (blank(rawTenantId)) return List.of();
        return Arrays.stream(new String[] { rawTenantId, rawTenantId.toUpperCase(Locale.ROOT), rawTenantId.toLowerCase(Locale.ROOT) })
                .map(value -> value == null ? null : value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !blank(value))
                .distinct()
                .toList();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record AgentPoolRoutingRow(
            String tenantId,
            String poolId,
            String poolCode,
            String poolName,
            String sourceSystem,
            String poolType,
            String selectionStrategy,
            String status,
            String agentId,
            String memberStatus,
            int priority,
            int weight) { }
}
