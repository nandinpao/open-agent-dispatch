package com.opensocket.aievent.core.iam.persistence.repository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.opensocket.aievent.core.iam.authentication.application.port.out.SecurityEpochPort;
import com.opensocket.aievent.core.iam.persistence.dao.IamAuthenticationDao;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import org.junit.jupiter.api.Test;

class MybatisTokenSecurityEpochAdapterTest {

    @Test
    void incrementPrincipalDoesNotIncrementTenantEpoch() {
        SecurityEpochPort delegate = mock(SecurityEpochPort.class);
        IamAuthenticationDao dao = mock(IamAuthenticationDao.class);
        MybatisTokenSecurityEpochAdapter adapter = new MybatisTokenSecurityEpochAdapter(delegate, dao);
        PrincipalRef principal = new PrincipalRef(PrincipalRef.PrincipalType.SERVICE_ACCOUNT, "svc-redmine");

        adapter.incrementPrincipal("tenant-a", principal, "admin-user");

        verify(dao).incrementPrincipalSecurityEpoch("tenant-a", "svc-redmine", "admin-user");
        verify(dao, never()).incrementTenantSecurityEpoch("tenant-a", "admin-user");
    }

    @Test
    void incrementPrincipalUsesGlobalPrincipalEpochWithoutTenant() {
        SecurityEpochPort delegate = mock(SecurityEpochPort.class);
        IamAuthenticationDao dao = mock(IamAuthenticationDao.class);
        MybatisTokenSecurityEpochAdapter adapter = new MybatisTokenSecurityEpochAdapter(delegate, dao);
        PrincipalRef principal = new PrincipalRef(PrincipalRef.PrincipalType.USER, "root-helper");

        adapter.incrementPrincipal("", principal, "root");

        verify(dao).incrementPrincipalSecurityEpoch("", "root-helper", "root");
    }
}
