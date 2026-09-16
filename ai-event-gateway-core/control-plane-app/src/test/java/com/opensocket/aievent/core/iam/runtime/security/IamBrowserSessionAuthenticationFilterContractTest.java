package com.opensocket.aievent.core.iam.runtime.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationDomainException;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationReasonCode;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IamBrowserSessionAuthenticationFilterContractTest {

    @Test
    void restrictedSessionsAllowPublicRuntimeCapabilityProbe() throws Exception {
        Field field = IamBrowserSessionAuthenticationFilter.class
                .getDeclaredField("SESSION_RECOVERY_ALLOWED_PATHS");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> allowedPaths = (Set<String>) field.get(null);

        assertThat(allowedPaths)
                .contains(
                        "/api/platform/runtime-capabilities",
                        "/api/platform/runtime-capabilities/");
    }

    @Test
    void rootBootstrapSessionAllowsOnlyBootstrapAndSessionRecoverySurfaces() throws Exception {
        IamBrowserSessionAuthenticationFilter filter =
                new IamBrowserSessionAuthenticationFilter(null, null, null);
        Method method = IamBrowserSessionAuthenticationFilter.class
                .getDeclaredMethod("allowedDuringRootBootstrap", HttpServletRequest.class);
        method.setAccessible(true);

        assertThat(invoke(method, filter, "/api/bootstrap/status")).isTrue();
        assertThat(invoke(method, filter, "/api/bootstrap/root/mfa")).isTrue();
        assertThat(invoke(method, filter, "/api/bootstrap/tenant")).isTrue();
        assertThat(invoke(method, filter, "/api/session/login")).isTrue();
        assertThat(invoke(method, filter, "/api/session/mfa/verify")).isTrue();
        assertThat(invoke(method, filter, "/api/session/forgot-password")).isTrue();
        assertThat(invoke(method, filter, "/api/session/reset-password")).isTrue();
        assertThat(invoke(method, filter, "/api/session/logout")).isTrue();
        assertThat(invoke(method, filter, "/api/admin/access/platform/users")).isFalse();
        assertThat(invoke(method, filter, "/admin/agents")).isFalse();
    }

    @Test
    void humanMfaEnrollmentSessionAllowsOnlyGuidedEnrollmentAndSessionRecoverySurfaces() throws Exception {
        IamBrowserSessionAuthenticationFilter filter =
                new IamBrowserSessionAuthenticationFilter(null, null, null);
        Method method = IamBrowserSessionAuthenticationFilter.class
                .getDeclaredMethod("allowedDuringHumanMfaEnrollment", HttpServletRequest.class);
        method.setAccessible(true);

        assertThat(invoke(method, filter, "/api/session")).isTrue();
        assertThat(invoke(method, filter, "/api/session/mfa/enrollment/start")).isTrue();
        assertThat(invoke(method, filter, "/api/session/mfa/enrollment/confirm")).isTrue();
        assertThat(invoke(method, filter, "/api/session/logout")).isTrue();
        assertThat(invoke(method, filter, "/api/platform/runtime-capabilities")).isTrue();
        assertThat(invoke(method, filter, "/api/admin/access/tenants/tenant-a/users")).isFalse();
        assertThat(invoke(method, filter, "/api/admin/access/tenants/tenant-a/roles")).isFalse();
    }

    @Test
    void transientOptimisticLockFailureMustNotDeleteTheBrowserSession() throws Exception {
        IamBrowserSessionAuthenticationFilter filter =
                new IamBrowserSessionAuthenticationFilter(null, null, null);
        Method method = IamBrowserSessionAuthenticationFilter.class
                .getDeclaredMethod("invalidatesSession", RuntimeException.class);
        method.setAccessible(true);

        boolean invalidates = (boolean) method.invoke(
                filter, new IamOptimisticLockException("BrowserSession", "session-1", 3));

        assertThat(invalidates).isFalse();
    }

    @Test
    void revokedSessionStillDeletesTheBrowserCookie() throws Exception {
        IamBrowserSessionAuthenticationFilter filter =
                new IamBrowserSessionAuthenticationFilter(null, null, null);
        Method method = IamBrowserSessionAuthenticationFilter.class
                .getDeclaredMethod("invalidatesSession", RuntimeException.class);
        method.setAccessible(true);

        boolean invalidates = (boolean) method.invoke(
                filter,
                new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_SESSION_REVOKED,
                        "Session is revoked"));

        assertThat(invalidates).isTrue();
    }

    private static boolean invoke(
            Method method,
            IamBrowserSessionAuthenticationFilter filter,
            String path) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        return (boolean) method.invoke(filter, request);
    }
}
