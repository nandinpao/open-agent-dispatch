package com.opensocket.aievent.core.http.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.runtime.security.IamRuntimeAuthenticationToken;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import com.opensocket.aievent.core.identity.AdminAccount;
import com.opensocket.aievent.core.identity.AdminPrincipal;
import com.opensocket.aievent.core.identity.AdminRole;

class OpenDispatchRequestContextFilterTest {
    private final OpenDispatchRequestContextFilter filter =
            new OpenDispatchRequestContextFilter(ObservationRegistry.create());

    @AfterEach
    void cleanThreadState() {
        SecurityContextHolder.clearContext();
        OpenDispatchRequestContextHolder.clear();
        IamTenantContextHolder.clear();
        MDC.clear();
    }

    @Test
    void shouldExposeRequestContextInsideChainAndRestoreMdcAfterRequest() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "core-internal-OPERATOR",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
        MDC.put("traceId", "parent-trace");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/agents");
        request.addHeader(OpenDispatchRequestContextFilter.REQUEST_ID_HEADER, "request-001");
        request.addHeader(OpenDispatchRequestContextFilter.CORRELATION_ID_HEADER, "correlation-001");
        request.addHeader(OpenDispatchRequestContextFilter.TENANT_ID_HEADER, "tenant-a");
        request.addHeader("User-Agent", "OpenDispatch-Test/1.0");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current().orElseThrow();
            assertThat(context.requestId()).isEqualTo("request-001");
            assertThat(context.correlationId()).isEqualTo("correlation-001");
            assertThat(context.tenantId()).isEqualTo("tenant-a");
            assertThat(context.operatorId()).isEqualTo("core-internal-OPERATOR");
            assertThat(context.clientAddress()).isEqualTo("192.0.2.10");
            assertThat(context.userAgent()).isEqualTo("OpenDispatch-Test/1.0");
            assertThat(context.requestKind()).isEqualTo("admin");
            assertThat(MDC.get("requestId")).isEqualTo("request-001");
            assertThat(MDC.get("tenantId")).isEqualTo("tenant-a");
            assertThat(MDC.get("operatorId")).isEqualTo("core-internal-OPERATOR");
        });

        assertThat(response.getHeader(OpenDispatchRequestContextFilter.REQUEST_ID_HEADER)).isEqualTo("request-001");
        assertThat(response.getHeader(OpenDispatchRequestContextFilter.CORRELATION_ID_HEADER)).isEqualTo("correlation-001");
        assertThat(OpenDispatchRequestContextHolder.current()).isEmpty();
        assertThat(MDC.get("traceId")).isEqualTo("parent-trace");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("operatorId")).isNull();
    }

    @Test
    void shouldNotLeakTenantOrOperatorToTheNextRequest() throws ServletException, IOException {
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/internal/control-plane/tasks/task-001");
        first.addHeader(OpenDispatchRequestContextFilter.TENANT_ID_HEADER, "tenant-first");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, (request, response) ->
                assertThat(OpenDispatchRequestContextHolder.current().orElseThrow().tenantId()).isEqualTo("tenant-first"));

        SecurityContextHolder.clearContext();
        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/core/status");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, (request, response) -> {
            OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current().orElseThrow();
            assertThat(context.tenantId()).isEmpty();
            assertThat(context.operatorId()).isEqualTo("anonymous");
            assertThat(MDC.get("tenantId")).isNull();
            assertThat(MDC.get("operatorId")).isEqualTo("anonymous");
        });

        assertThat(OpenDispatchRequestContextHolder.current()).isEmpty();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void shouldUseSessionSelectedTenantInsteadOfUntrustedHeaderOrQueryParameter()
            throws ServletException, IOException {
        AdminPrincipal principal = legacyPrincipal();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/a2a-operations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(OpenDispatchRequestContextHolder.current().orElseThrow().tenantId())
                        .isEqualTo("tenant-b"));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldUseLegacySessionSelectedTenantWhenTheRequestedTenantMatches() throws ServletException, IOException {
        AdminPrincipal principal = legacyPrincipal();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/a2a-operations");
        request.addHeader(OpenDispatchRequestContextFilter.TENANT_ID_HEADER, "tenant-b");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current().orElseThrow();
            assertThat(context.tenantId()).isEqualTo("tenant-b");
            assertThat(context.operatorId()).isEqualTo("admin");
        });

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectTenantSpoofingAgainstLegacySessionWorkspace() throws ServletException, IOException {
        AdminPrincipal principal = legacyPrincipal();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/a2a-operations");
        request.addHeader(OpenDispatchRequestContextFilter.TENANT_ID_HEADER, "tenant-attacker");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TENANT_CONTEXT_MISMATCH");
    }

    @Test
    void shouldResolveTenantAndOperatorFromIamAuthenticationContext() throws ServletException, IOException {
        AuthenticationContext context = iamContext("tenant-a", "user-a");
        SecurityContextHolder.getContext().setAuthentication(new IamRuntimeAuthenticationToken(context));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/a2a-operations");
        request.addHeader(OpenDispatchRequestContextFilter.TENANT_ID_HEADER, "tenant-a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            OpenDispatchRequestContext current = OpenDispatchRequestContextHolder.current().orElseThrow();
            assertThat(current.tenantId()).isEqualTo("tenant-a");
            assertThat(current.operatorId()).isEqualTo("user-a");
        });

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectTenantSpoofingAgainstIamWorkspace() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(new IamRuntimeAuthenticationToken(iamContext("tenant-a", "user-a")));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/a2a-operations");
        request.addHeader(OpenDispatchRequestContextFilter.TENANT_ID_HEADER, "tenant-b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TENANT_CONTEXT_MISMATCH");
    }

    @Test
    void instanceRootMaySelectAnyTenantWithoutChangingItsInstanceSession()
            throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new IamRuntimeAuthenticationToken(rootIamContext("root")));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/admin/access/tenants/tenant-b/users");
        request.addHeader(OpenDispatchRequestContextFilter.TENANT_ID_HEADER, "tenant-b");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            OpenDispatchRequestContext current = OpenDispatchRequestContextHolder.current().orElseThrow();
            assertThat(current.tenantId()).isEqualTo("tenant-b");
            assertThat(current.operatorId()).isEqualTo("root");
            assertThat(IamTenantContextHolder.require().tenantId()).isEqualTo("tenant-b");
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(IamTenantContextHolder.current()).isEmpty();
    }

    @Test
    void anonymousRequestDoesNotOpenPrivilegedInstancePersistenceContext()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session/csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(IamTenantContextHolder.current()).isEmpty());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldDecodeUtf8AuditReasonForDownstreamMutationHandling() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "DELETE", "/api/admin/access/tenants/tenant-a/departments/department-a");
        request.addHeader(OpenDispatchRequestContextFilter.AUDIT_REASON_HEADER,
                "od-utf8:%E6%B8%AC%E8%A9%A6%E5%88%AA%E9%99%A4");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(((jakarta.servlet.http.HttpServletRequest) servletRequest)
                        .getHeader(OpenDispatchRequestContextFilter.AUDIT_REASON_HEADER))
                        .isEqualTo("測試刪除"));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectUnsafeIncomingRequestIdAndGenerateSafeReplacement() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/core/status");
        request.addHeader(OpenDispatchRequestContextFilter.REQUEST_ID_HEADER, "unsafe request id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String requestId = OpenDispatchRequestContextHolder.current().orElseThrow().requestId();
            assertThat(requestId).isNotBlank().doesNotContain(" ");
        });

        assertThat(response.getHeader(OpenDispatchRequestContextFilter.REQUEST_ID_HEADER))
                .isNotBlank()
                .doesNotContain(" ");
    }

    private AdminPrincipal legacyPrincipal() {
        return AdminPrincipal.from(new AdminAccount(
                "user-1", "admin", "Administrator", "{noop}secret", Set.of(AdminRole.ADMIN),
                Set.of("tenant-a", "tenant-b"), "tenant-b", true));
    }


    private AuthenticationContext rootIamContext(String principalId) {
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        return new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.INSTANCE_ROOT, principalId),
                new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, principalId),
                TenantRef.instance(),
                Optional.empty(),
                AuthenticationAssurance.passwordOnly(now),
                SecurityEpoch.ZERO,
                Optional.empty(),
                now.minusSeconds(10),
                now.plusSeconds(600));
    }

    private AuthenticationContext iamContext(String tenantId, String principalId) {
        Instant now = Instant.parse("2026-08-02T00:00:00Z");
        return new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.HUMAN_USER, principalId),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, principalId),
                TenantRef.tenant(tenantId),
                Optional.empty(),
                AuthenticationAssurance.passwordOnly(now),
                SecurityEpoch.ZERO,
                Optional.empty(),
                now.minusSeconds(10),
                now.plusSeconds(600));
    }
}
