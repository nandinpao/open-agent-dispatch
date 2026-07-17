package com.opensocket.aievent.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class CoreInternalTokenAuthenticationFilterTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recoveryApproverDoesNotInheritRecoveryAdminOrRecoveryOperatorAuthority() throws ServletException, IOException {
        CoreInternalSecurityProperties properties = new CoreInternalSecurityProperties();
        properties.setEnabled(true);
        properties.setRecoveryApproverToken("approver-secret");
        CoreInternalSecurityRequestClassifier classifier = new CoreInternalSecurityRequestClassifier(properties);
        CoreInternalTokenAuthenticationFilter filter = new CoreInternalTokenAuthenticationFilter(
                properties, new CoreInternalTokenVerifier(properties, classifier));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/admin/recovery/approval-requests/request-1/approve");
        request.addHeader("X-Cluster-Token", "approver-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
            assertThat(authorities).extracting(Object::toString)
                    .contains("ROLE_RECOVERY_APPROVER", "ROLE_OPERATOR")
                    .doesNotContain("ROLE_RECOVERY_ADMIN", "ROLE_RECOVERY_OPERATOR");
        });

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void existingHumanSessionIsNotOverwrittenByMachineTokenFilter() throws ServletException, IOException {
        CoreInternalSecurityProperties properties = new CoreInternalSecurityProperties();
        properties.setEnabled(true);
        properties.setOperatorToken("operator-secret");
        CoreInternalSecurityRequestClassifier classifier = new CoreInternalSecurityRequestClassifier(properties);
        CoreInternalTokenAuthenticationFilter filter = new CoreInternalTokenAuthenticationFilter(
                properties, new CoreInternalTokenVerifier(properties, classifier));
        var human = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "human-admin", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(human);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(human));
    }
}
