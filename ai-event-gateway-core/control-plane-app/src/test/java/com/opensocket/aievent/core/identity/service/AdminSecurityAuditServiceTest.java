package com.opensocket.aievent.core.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.opensocket.aievent.core.identity.AdminAccount;
import com.opensocket.aievent.core.identity.AdminPrincipal;
import com.opensocket.aievent.core.identity.AdminRole;

class AdminSecurityAuditServiceTest {
    @Test
    void recordsSanitizedAuthenticationEventsAndRestrictsQueriesToAdmin() {
        AdminSecurityAuditService service = new AdminSecurityAuditService();
        AdminPrincipal admin = principal("admin", AdminRole.ADMIN);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession(null, "secret-session-id"));
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "browser\r\nforged");

        service.record("LOGIN", "SUCCESS", "admin", admin, request, "accepted\r\nforged");
        var authentication = UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities());
        var events = service.recent(authentication, 10).events();

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("LOGIN");
            assertThat(event.sessionReference()).doesNotContain("secret-session-id");
            assertThat(event.userAgent()).doesNotContain("\r").doesNotContain("\n");
            assertThat(event.reason()).doesNotContain("\r").doesNotContain("\n");
        });

        AdminPrincipal viewer = principal("viewer", AdminRole.VIEWER);
        var viewerAuth = UsernamePasswordAuthenticationToken.authenticated(viewer, null, viewer.getAuthorities());
        assertThatThrownBy(() -> service.recent(viewerAuth, 10))
                .isInstanceOf(AdminSessionAccessDeniedException.class);
    }

    private AdminPrincipal principal(String username, AdminRole role) {
        return AdminPrincipal.from(new AdminAccount("user-" + username, username, username, "{noop}secret",
                Set.of(role), Set.of("tenant-a"), "tenant-a", true));
    }
}
