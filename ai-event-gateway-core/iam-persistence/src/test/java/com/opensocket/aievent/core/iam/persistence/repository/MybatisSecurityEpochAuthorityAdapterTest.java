package com.opensocket.aievent.core.iam.persistence.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.opensocket.aievent.core.iam.authentication.application.port.out.SecurityEpochPort;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import org.junit.jupiter.api.Test;

class MybatisSecurityEpochAuthorityAdapterTest {

    @Test
    void projectedInstanceRootUsesOnlyInstanceEpochAuthority() {
        RecordingEpochPort delegate = new RecordingEpochPort();
        MybatisSecurityEpochAuthorityAdapter adapter = new MybatisSecurityEpochAuthorityAdapter(delegate);

        SecurityEpoch result = adapter.current(
                TenantRef.tenant("tenant-a"),
                new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root"));

        assertEquals("", delegate.lastTenantId);
        assertEquals("root", delegate.lastPrincipalId);
        assertEquals(new SecurityEpoch(11, 0, 13), result);
    }

    @Test
    void tenantUserStillUsesTenantEpochAuthority() {
        RecordingEpochPort delegate = new RecordingEpochPort();
        MybatisSecurityEpochAuthorityAdapter adapter = new MybatisSecurityEpochAuthorityAdapter(delegate);

        SecurityEpoch result = adapter.current(
                TenantRef.tenant("tenant-a"),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-a"));

        assertEquals("tenant-a", delegate.lastTenantId);
        assertEquals("user-a", delegate.lastPrincipalId);
        assertEquals(new SecurityEpoch(11, 12, 13), result);
    }

    private static final class RecordingEpochPort implements SecurityEpochPort {
        private String lastTenantId;
        private String lastPrincipalId;

        @Override
        public SecurityEpoch current(String tenantId, String principalId) {
            lastTenantId = tenantId;
            lastPrincipalId = principalId;
            return tenantId == null || tenantId.isBlank()
                    ? new SecurityEpoch(11, 0, 13)
                    : new SecurityEpoch(11, 12, 13);
        }

        @Override
        public SecurityEpoch increment(String tenantId, String principalId, String actorId) {
            throw new UnsupportedOperationException();
        }
    }
}
