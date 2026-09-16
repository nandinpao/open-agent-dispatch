package com.opensocket.aievent.core.iam.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.iam.runtime.security.IamBrowserSessionAuthenticationFilter;
import com.opensocket.aievent.core.security.CoreInternalSecurityConfiguration;
import com.opensocket.aievent.core.security.CoreInternalTokenAuthenticationFilter;
import com.opensocket.aievent.core.security.R3HttpAuthorizationFilter;
import org.junit.jupiter.api.Test;

/**
 * Security filters inserted into explicit SecurityFilterChains must not also be registered
 * as container-wide servlet filters. A global registration bypasses the chain request matcher
 * and can make Human R3 authorization intercept NON_HUMAN routes such as /api/events/**.
 */
class SecurityChainManagedFilterRegistrationTest {

    @Test
    void coreInternalTokenFilterMustOnlyRunInsideConfiguredSecurityChains() {
        var configuration = new CoreInternalSecurityConfiguration();
        var filter = new CoreInternalTokenAuthenticationFilter(null, null);
        assertThat(configuration
                .disableCoreInternalTokenAuthenticationFilterServletRegistration(filter)
                .isEnabled()).isFalse();
    }

    @Test
    void iamBrowserSessionFilterMustOnlyRunInsideConfiguredSecurityChains() {
        var configuration = new IamRuntimeConfiguration();
        var filter = new IamBrowserSessionAuthenticationFilter(null, null, null);
        assertThat(configuration
                .disableIamBrowserSessionAuthenticationFilterServletRegistration(filter)
                .isEnabled()).isFalse();
    }

    @Test
    void r3AuthorizationFilterMustOnlyRunInsideHumanSecurityChains() {
        var configuration = new IamRuntimeConfiguration();
        var filter = new R3HttpAuthorizationFilter(null, null, null);
        assertThat(configuration
                .disableR3HttpAuthorizationFilterServletRegistration(filter)
                .isEnabled()).isFalse();
    }
}
