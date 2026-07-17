package com.opensocket.aievent.core.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import com.opensocket.aievent.core.identity.AdminAccount;
import com.opensocket.aievent.core.identity.AdminIdentityRepository;
import com.opensocket.aievent.core.identity.AdminPrincipal;
import com.opensocket.aievent.core.identity.AdminRole;

class AdminSessionManagementServiceTest {
    @SuppressWarnings("unchecked")
    @Test
    void listsBrowserSafeReferencesAndRevokesByReference() {
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        AdminIdentityRepository identities = mock(AdminIdentityRepository.class);
        AdminSecurityAuditService audit = mock(AdminSecurityAuditService.class);
        AdminPrincipal admin = principal("admin", AdminRole.ADMIN);
        MapSession stored = storedSession("server-side-secret-session-id", admin);
        when(identities.findAll()).thenReturn(List.of(account("admin", AdminRole.ADMIN)));
        when(repository.findByPrincipalName("admin")).thenReturn(Map.of(stored.getId(), stored));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession(null, stored.getId()));
        AdminSessionManagementService service = new AdminSessionManagementService(repository, identities, audit);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities());

        var response = service.list(authentication, request);
        assertThat(response.currentSessionReference()).isEqualTo(AdminSessionManagementService.reference(stored.getId()));
        assertThat(response.sessions()).singleElement().satisfies(session -> {
            assertThat(session.sessionReference()).isEqualTo(AdminSessionManagementService.reference(stored.getId()));
            assertThat(session.sessionReference()).doesNotContain(stored.getId());
            assertThat(session.current()).isTrue();
        });

        var revoked = service.revoke(response.sessions().getFirst().sessionReference(), authentication, request);
        assertThat(revoked.status()).isEqualTo("REVOKED");
        assertThat(revoked.currentSession()).isTrue();
        verify(repository).deleteById(stored.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void nonAdminCannotDiscoverOrRevokeAnotherUsersSession() {
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        AdminIdentityRepository identities = mock(AdminIdentityRepository.class);
        AdminPrincipal viewer = principal("viewer", AdminRole.VIEWER);
        MapSession adminSession = storedSession("admin-session", principal("admin", AdminRole.ADMIN));
        when(repository.findByPrincipalName("viewer")).thenReturn(Map.of());
        AdminSessionManagementService service = new AdminSessionManagementService(
                repository, identities, mock(AdminSecurityAuditService.class));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(viewer, null, viewer.getAuthorities());

        assertThatThrownBy(() -> service.revoke(
                AdminSessionManagementService.reference(adminSession.getId()), authentication, new MockHttpServletRequest()))
                .isInstanceOf(AdminSessionNotFoundException.class);
    }

    private MapSession storedSession(String id, AdminPrincipal principal) {
        MapSession session = new MapSession(id);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }

    private AdminPrincipal principal(String username, AdminRole role) {
        return AdminPrincipal.from(account(username, role));
    }

    private AdminAccount account(String username, AdminRole role) {
        return new AdminAccount("user-" + username, username, username, "{noop}secret", Set.of(role),
                Set.of("tenant-a", "tenant-b"), "tenant-a", true);
    }
}
