package com.opensocket.aievent.core.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.opensocket.aievent.core.identity.AdminAccount;
import com.opensocket.aievent.core.identity.AdminIdentityProperties;
import com.opensocket.aievent.core.identity.AdminPrincipal;
import com.opensocket.aievent.core.identity.AdminRole;
import com.opensocket.aievent.core.identity.dto.AdminLoginRequest;

class AdminAuthenticationServiceTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsServerSideSessionAndPersistsSelectedTenant() {
        AdminIdentityProperties properties = properties(true);
        AdminPrincipal principal = principal();
        AuthenticationManager manager = ignored -> new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        AdminAuthenticationService service = new AdminAuthenticationService(
                properties, manager, repository, new NullAuthenticatedSessionStrategy(), mock(AdminSecurityAuditService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var login = service.login(new AdminLoginRequest("admin", "secret"), request, response);
        assertThat(request.getSession(false)).isNotNull();
        assertThat(login.authenticationType()).isEqualTo("SESSION");
        assertThat(login.selectedTenantId()).isEqualTo("tenant-a");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var selected = service.selectTenant("tenant-b", authentication, request, response);
        assertThat(selected.selectedTenantId()).isEqualTo("tenant-b");
        assertThat(((AdminPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .selectedTenantId()).isEqualTo("tenant-b");
    }

    @Test
    void rejectsLoginWhenCoreHumanAuthenticationIsDisabled() {
        AdminAuthenticationService service = new AdminAuthenticationService(
                properties(false), ignored -> { throw new AssertionError("must not authenticate"); },
                new HttpSessionSecurityContextRepository(), new NullAuthenticatedSessionStrategy(), mock(AdminSecurityAuditService.class));

        assertThatThrownBy(() -> service.login(new AdminLoginRequest("admin", "secret"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(AdminAuthenticationDisabledException.class);
    }

    @Test
    void rejectsMachinePrincipalForHumanIdentityEndpoints() {
        AdminAuthenticationService service = new AdminAuthenticationService(
                properties(true), ignored -> ignored, new HttpSessionSecurityContextRepository(),
                new NullAuthenticatedSessionStrategy(), mock(AdminSecurityAuditService.class));
        Authentication machine = UsernamePasswordAuthenticationToken.authenticated(
                "core-internal-OPERATOR", "n/a", Set.of());

        assertThatThrownBy(() -> service.permissions(machine))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Core Admin principal");
    }

    private AdminIdentityProperties properties(boolean enabled) {
        AdminIdentityProperties properties = new AdminIdentityProperties();
        properties.setEnabled(enabled);
        properties.setSessionTimeout(Duration.ofMinutes(30));
        return properties;
    }

    private AdminPrincipal principal() {
        return AdminPrincipal.from(new AdminAccount(
                "user-1", "admin", "Administrator", "{noop}secret", Set.of(AdminRole.ADMIN),
                Set.of("tenant-a", "tenant-b"), "tenant-a", true));
    }
}
