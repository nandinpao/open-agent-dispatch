package com.opensocket.aievent.core.dispatch.flow;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;

/**
 * Binds the authenticated OpenDispatch Tenant/actor to PostgreSQL's transaction-local
 * RLS context before Dispatch Flow / Agent Pool management or Dispatch Simulation direct-JDBC access uses NamedParameterJdbcTemplate.
 *
 * <p>MyBatis persistence is covered by TenantTransactionMybatisInterceptor, but direct
 * JdbcTemplate/NamedParameterJdbcTemplate calls bypass that interceptor. Tenant-owned
 * tables whose RLS policies call {@code iam_current_tenant_id()} therefore require the
 * same transaction-local {@code app.current_tenant_id}/{@code app.current_actor_id}
 * variables to be established explicitly on the Spring-bound JDBC connection.</p>
 */
final class DispatchFlowPersistenceTenantContext {
    private DispatchFlowPersistenceTenantContext() {}

    static void bind(NamedParameterJdbcTemplate jdbc, String tenantId, String fallbackActor) {
        String tenant = required(tenantId, "tenantId");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("TENANT_TRANSACTION_REQUIRED: Dispatch persistence");
        }

        IamTenantExecutionContext current = IamTenantContextHolder.current().orElse(null);
        if (current != null
                && !"INSTANCE".equalsIgnoreCase(current.tenantId())
                && !tenant.equals(current.tenantId())) {
            throw new IllegalStateException("TENANT_CONTEXT_MISMATCH: Dispatch persistence");
        }

        String actor = current == null || blank(current.actorId())
                ? required(fallbackActor, "fallbackActor")
                : current.actorId().trim();

        MapSqlParameterSource context = new MapSqlParameterSource()
                .addValue("tenantId", tenant)
                .addValue("actorId", actor);
        jdbc.queryForObject(
                "select set_config('app.current_tenant_id', :tenantId, true) || ':' || "
                        + "set_config('app.current_actor_id', :actorId, true)",
                context,
                String.class);
    }

    private static String required(String value, String field) {
        if (blank(value)) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
