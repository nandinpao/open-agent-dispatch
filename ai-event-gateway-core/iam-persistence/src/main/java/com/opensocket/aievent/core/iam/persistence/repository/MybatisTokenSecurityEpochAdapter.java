package com.opensocket.aievent.core.iam.persistence.repository;

import com.opensocket.aievent.core.iam.authentication.application.port.out.SecurityEpochPort;
import com.opensocket.aievent.core.iam.persistence.dao.IamAuthenticationDao;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.token.application.port.out.TokenSecurityEpochPort;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

/** Token-facing security epoch adapter. Principal revocation invalidates only that principal. */
@DatabaseRepositoryAdapter
public class MybatisTokenSecurityEpochAdapter implements TokenSecurityEpochPort {
    private final SecurityEpochPort delegate;
    private final IamAuthenticationDao dao;

    public MybatisTokenSecurityEpochAdapter(SecurityEpochPort delegate, IamAuthenticationDao dao) {
        this.delegate = delegate;
        this.dao = dao;
    }

    @Override
    public SecurityEpoch current(String tenantId, PrincipalRef principal) {
        return delegate.current(tenantId, principal.principalId());
    }

    @Override
    public void incrementPrincipal(String tenantId, PrincipalRef principal, String actorId) {
        String normalizedTenant = tenantId == null ? "" : tenantId.trim();
        dao.incrementPrincipalSecurityEpoch(normalizedTenant, principal.principalId(), actorId);
    }
}
