package com.opensocket.aievent.core.iam.runtime.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IamPersonalAccessTokenAuthenticationFilterTest {
    @Test
    void mapsGovernedApiFamiliesToCredentialCatalogAudiences() {
        assertThat(IamPersonalAccessTokenAuthenticationFilter.audienceFor("/api/admin/access/tenants/tenant-a/users"))
                .isEqualTo("OPEN_DISPATCH_ADMIN");
        assertThat(IamPersonalAccessTokenAuthenticationFilter.audienceFor("/api/admin/tasks/task-1/runtime-view"))
                .isEqualTo("OPEN_DISPATCH_RUNTIME");
        assertThat(IamPersonalAccessTokenAuthenticationFilter.audienceFor("/api/tasks/task-1"))
                .isEqualTo("OPEN_DISPATCH_RUNTIME");
        assertThat(IamPersonalAccessTokenAuthenticationFilter.audienceFor("/api/integrations/connections"))
                .isEqualTo("OPEN_DISPATCH_INTEGRATION");
        assertThat(IamPersonalAccessTokenAuthenticationFilter.audienceFor("/api/admin/access/tenants/tenant-a/audit-feed"))
                .isEqualTo("OPEN_DISPATCH_AUDIT");
    }

    @Test
    void rejectsPatUseOnIdentityBootstrapEventAndInternalTrustSurfaces() {
        assertThat(IamPersonalAccessTokenAuthenticationFilter.patForbiddenPath("/api/session/login")).isTrue();
        assertThat(IamPersonalAccessTokenAuthenticationFilter.patForbiddenPath("/api/bootstrap/status")).isTrue();
        assertThat(IamPersonalAccessTokenAuthenticationFilter.patForbiddenPath("/api/events/intake")).isTrue();
        assertThat(IamPersonalAccessTokenAuthenticationFilter.patForbiddenPath("/oauth/token")).isTrue();
        assertThat(IamPersonalAccessTokenAuthenticationFilter.patForbiddenPath("/internal/health")).isTrue();
        assertThat(IamPersonalAccessTokenAuthenticationFilter.patForbiddenPath("/api/admin/tasks/task-1")).isFalse();
    }
}
