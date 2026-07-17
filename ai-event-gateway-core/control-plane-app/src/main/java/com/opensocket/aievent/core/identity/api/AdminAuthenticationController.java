package com.opensocket.aievent.core.identity.api;

import java.time.OffsetDateTime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.identity.dto.AdminCsrfResponse;
import com.opensocket.aievent.core.identity.dto.AdminLoginRequest;
import com.opensocket.aievent.core.identity.dto.AdminLogoutResponse;
import com.opensocket.aievent.core.identity.dto.AdminPermissionsResponse;
import com.opensocket.aievent.core.identity.dto.AdminSessionResponse;
import com.opensocket.aievent.core.identity.dto.AdminSessionsResponse;
import com.opensocket.aievent.core.identity.dto.AdminSessionRevocationResponse;
import com.opensocket.aievent.core.identity.dto.AdminSecurityAuditResponse;
import com.opensocket.aievent.core.identity.dto.AdminTenantSelectionRequest;
import com.opensocket.aievent.core.identity.dto.AdminTenantsResponse;
import com.opensocket.aievent.core.identity.service.AdminAuthenticationService;
import com.opensocket.aievent.core.identity.service.AdminSessionManagementService;
import com.opensocket.aievent.core.identity.service.AdminSecurityAuditService;

@RestController
@RequestMapping("/api/auth")
public class AdminAuthenticationController {
    private final AdminAuthenticationService authenticationService;
    private final AdminSessionManagementService sessionManagementService;
    private final AdminSecurityAuditService securityAuditService;

    public AdminAuthenticationController(AdminAuthenticationService authenticationService,
                                         AdminSessionManagementService sessionManagementService,
                                         AdminSecurityAuditService securityAuditService) {
        this.authenticationService = authenticationService;
        this.sessionManagementService = sessionManagementService;
        this.securityAuditService = securityAuditService;
    }

    @PostMapping("/login")
    public AdminSessionResponse login(@Valid @RequestBody AdminLoginRequest request,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        return authenticationService.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    public AdminLogoutResponse logout(Authentication authentication,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        authenticationService.logout(authentication, request, response);
        return new AdminLogoutResponse("LOGGED_OUT", OffsetDateTime.now());
    }

    @GetMapping("/me")
    public AdminSessionResponse me(Authentication authentication, HttpServletRequest request) {
        return authenticationService.current(authentication, request);
    }

    @GetMapping("/permissions")
    public AdminPermissionsResponse permissions(Authentication authentication) {
        return authenticationService.permissions(authentication);
    }

    @GetMapping("/tenants")
    public AdminTenantsResponse tenants(Authentication authentication) {
        return authenticationService.tenants(authentication);
    }

    @PostMapping("/select-tenant")
    public AdminSessionResponse selectTenant(@Valid @RequestBody AdminTenantSelectionRequest request,
                                             Authentication authentication,
                                             HttpServletRequest httpRequest,
                                             HttpServletResponse httpResponse) {
        return authenticationService.selectTenant(request.tenantId(), authentication, httpRequest, httpResponse);
    }

    @GetMapping("/sessions")
    public AdminSessionsResponse sessions(Authentication authentication, HttpServletRequest request) {
        return sessionManagementService.list(authentication, request);
    }

    @PostMapping("/sessions/{sessionReference}/revoke")
    public AdminSessionRevocationResponse revokeSession(@PathVariable String sessionReference,
                                                        Authentication authentication,
                                                        HttpServletRequest request) {
        return sessionManagementService.revoke(sessionReference, authentication, request);
    }

    @GetMapping("/security-audit")
    public AdminSecurityAuditResponse securityAudit(@RequestParam(defaultValue = "100") int limit,
                                                    Authentication authentication) {
        return securityAuditService.recent(authentication, limit);
    }

    @GetMapping("/csrf")
    public AdminCsrfResponse csrf(CsrfToken token) {
        return new AdminCsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }
}
