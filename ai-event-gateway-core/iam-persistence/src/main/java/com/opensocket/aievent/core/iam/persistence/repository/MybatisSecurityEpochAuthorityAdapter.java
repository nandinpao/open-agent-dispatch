package com.opensocket.aievent.core.iam.persistence.repository;

import com.opensocket.aievent.core.iam.authentication.application.port.out.SecurityEpochPort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.SecurityEpochAuthorityPort;
import com.opensocket.aievent.core.iam.security.contract.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

/**
 * Resolves the authoritative epoch used by RBAC.
 *
 * <p>INSTANCE_ROOT owns an INSTANCE-scoped break-glass session. A Human route may project that
 * session into one explicitly addressed Tenant for scope validation and audit, but Tenant role
 * bindings and Tenant security epochs do not govern the Root principal. Reading Tenant epochs
 * for a projected Root request adds a false persistence dependency and can turn a valid Root
 * administration request into AUTHORIZATION_SERVICE_UNAVAILABLE when Tenant epoch state is
 * unavailable. Keep Root freshness anchored to global + global-principal epochs only.</p>
 */
@DatabaseRepositoryAdapter
public class MybatisSecurityEpochAuthorityAdapter implements SecurityEpochAuthorityPort {
    private final SecurityEpochPort delegate;

    public MybatisSecurityEpochAuthorityAdapter(SecurityEpochPort delegate) {
        this.delegate = delegate;
    }

    @Override
    public SecurityEpoch current(TenantRef tenant, PrincipalRef principal) {
        if (principal.principalType() == PrincipalRef.PrincipalType.INSTANCE_ROOT) {
            return delegate.current("", principal.principalId());
        }
        String tenantId = tenant.scope() == TenantRef.Scope.TENANT ? tenant.tenantId() : "";
        return delegate.current(tenantId, principal.principalId());
    }
}
