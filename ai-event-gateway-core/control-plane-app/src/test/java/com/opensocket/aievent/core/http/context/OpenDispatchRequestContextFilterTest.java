package com.opensocket.aievent.core.http.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Set;

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
    void shouldUseSessionSelectedTenantInsteadOfUntrustedHeaderOrQueryParameter() throws ServletException, IOException {
        AdminPrincipal principal = AdminPrincipal.from(new AdminAccount(
                "user-1", "admin", "Administrator", "{noop}secret", Set.of(AdminRole.ADMIN),
                Set.of("tenant-a", "tenant-b"), "tenant-b", true));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/tasks");
        request.addHeader(OpenDispatchRequestContextFilter.TENANT_ID_HEADER, "tenant-attacker");
        request.setParameter("tenantId", "tenant-query");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current().orElseThrow();
            assertThat(context.tenantId()).isEqualTo("tenant-b");
            assertThat(context.operatorId()).isEqualTo("admin");
        });
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
}
