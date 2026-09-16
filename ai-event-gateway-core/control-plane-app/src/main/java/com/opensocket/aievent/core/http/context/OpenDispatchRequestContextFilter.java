package com.opensocket.aievent.core.http.context;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.opensocket.aievent.core.http.observation.OpenDispatchHttpObservationKeys;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import com.opensocket.aievent.core.identity.AdminPrincipal;

/**
 * Establishes request diagnostics after authentication. Spring owns the HTTP root observation;
 * this filter only enriches that observation and manages request-local state.
 */
public class OpenDispatchRequestContextFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String AUDIT_REASON_HEADER = "X-Audit-Reason";
    private static final String AUDIT_REASON_UTF8_PREFIX = "od-utf8:";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}");
    private static final int MAX_USER_AGENT_LENGTH = 256;
    private static final int MAX_CLIENT_ADDRESS_LENGTH = 128;

    private final ObservationRegistry observationRegistry;
    private final com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver trustedClientIpResolver;

    /** Backward-compatible test/embedded constructor: forwarding headers remain untrusted by default. */
    public OpenDispatchRequestContextFilter(ObservationRegistry observationRegistry) {
        this(observationRegistry, new com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver(
                new com.opensocket.aievent.core.iam.runtime.config.IamMachineTokenProperties()));
    }

    public OpenDispatchRequestContextFilter(ObservationRegistry observationRegistry,
            com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver trustedClientIpResolver) {
        this.observationRegistry = observationRegistry;
        this.trustedClientIpResolver = trustedClientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = safeIdentifier(request.getHeader(REQUEST_ID_HEADER), UUID.randomUUID().toString());
        String correlationId = safeIdentifier(request.getHeader(CORRELATION_ID_HEADER), requestId);
        TenantResolution tenantResolution = resolveTenantId(request);

        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        if (tenantResolution.mismatch()) {
            rejectTenantMismatch(response);
            return;
        }

        String tenantId = tenantResolution.tenantId();
        String operatorId = resolveOperatorId();
        String clientAddress = bounded(trustedClientIpResolver.resolve(request), MAX_CLIENT_ADDRESS_LENGTH, "unknown");
        String userAgent = bounded(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH, "unknown");
        String requestKind = classifyRequest(request.getRequestURI());
        boolean authenticated = !"anonymous".equals(operatorId);

        OpenDispatchRequestContext context = new OpenDispatchRequestContext(
                requestId, correlationId, tenantId, operatorId, clientAddress, userAgent, requestKind);
        Map<String, String> mdc = new LinkedHashMap<>();
        mdc.put("requestId", requestId);
        mdc.put("correlationId", correlationId);
        mdc.put("tenantId", tenantId);
        mdc.put("operatorId", operatorId);
        mdc.put("clientIp", clientAddress);

        setObservationAttributes(request, context, authenticated);
        enrichCurrentObservation(context, authenticated);

        String persistenceScope = tenantId.isBlank() ? "INSTANCE" : tenantId;
        IamTenantContextHolder.Scope iamScope = authenticated
                ? IamTenantContextHolder.open(new IamTenantExecutionContext(persistenceScope, operatorId))
                : null;
        HttpServletRequest downstreamRequest = decodeAuditReasonHeader(request);
        try (OpenDispatchRequestContextHolder.Scope ignored = OpenDispatchRequestContextHolder.open(context);
             MdcContextScope ignoredMdc = MdcContextScope.open(mdc);
             IamTenantContextHolder.Scope ignoredIam = iamScope) {
            filterChain.doFilter(downstreamRequest, response);
        }
    }

    private HttpServletRequest decodeAuditReasonHeader(HttpServletRequest request) {
        String encoded = request.getHeader(AUDIT_REASON_HEADER);
        if (encoded == null || !encoded.startsWith(AUDIT_REASON_UTF8_PREFIX)) {
            return request;
        }
        String decoded = decodeAuditReasonValue(encoded);
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (AUDIT_REASON_HEADER.equalsIgnoreCase(name)) return decoded;
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (AUDIT_REASON_HEADER.equalsIgnoreCase(name)) return Collections.enumeration(java.util.List.of(decoded));
                return super.getHeaders(name);
            }
        };
    }

    private String decodeAuditReasonValue(String encoded) {
        try {
            String decoded = URLDecoder.decode(encoded.substring(AUDIT_REASON_UTF8_PREFIX.length()), StandardCharsets.UTF_8);
            return decoded.replace('\r', ' ').replace('\n', ' ').trim();
        } catch (IllegalArgumentException malformedEncoding) {
            return encoded;
        }
    }

    private void rejectTenantMismatch(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"TENANT_CONTEXT_MISMATCH\","
                + "\"message\":\"Requested Tenant does not match the authenticated workspace.\","
                + "\"data\":null,\"timestamp\":\"" + Instant.now() + "\"}");
    }

    private void setObservationAttributes(HttpServletRequest request,
                                          OpenDispatchRequestContext context,
                                          boolean authenticated) {
        setAttribute(request, OpenDispatchHttpObservationKeys.REQUEST_ID, context.requestId());
        setAttribute(request, OpenDispatchHttpObservationKeys.CORRELATION_ID, context.correlationId());
        setAttribute(request, OpenDispatchHttpObservationKeys.TENANT_ID, context.tenantId());
        setAttribute(request, OpenDispatchHttpObservationKeys.OPERATOR_ID, context.operatorId());
        setAttribute(request, OpenDispatchHttpObservationKeys.CLIENT_ADDRESS, context.clientAddress());
        setAttribute(request, OpenDispatchHttpObservationKeys.USER_AGENT, context.userAgent());
        setAttribute(request, OpenDispatchHttpObservationKeys.REQUEST_KIND, context.requestKind());
        setAttribute(request, OpenDispatchHttpObservationKeys.AUTHENTICATED, Boolean.toString(authenticated));
        setAttribute(request, OpenDispatchHttpObservationKeys.TENANT_PRESENT, Boolean.toString(!context.tenantId().isBlank()));
    }

    private void setAttribute(HttpServletRequest request, String key, String value) {
        request.setAttribute(OpenDispatchHttpObservationKeys.ATTR_PREFIX + key, value);
    }

    private void enrichCurrentObservation(OpenDispatchRequestContext context, boolean authenticated) {
        Observation current = observationRegistry.getCurrentObservation();
        if (current == null) {
            return;
        }
        current.lowCardinalityKeyValue(OpenDispatchHttpObservationKeys.REQUEST_KIND, context.requestKind())
                .lowCardinalityKeyValue(OpenDispatchHttpObservationKeys.AUTHENTICATED, Boolean.toString(authenticated))
                .lowCardinalityKeyValue(OpenDispatchHttpObservationKeys.TENANT_PRESENT, Boolean.toString(!context.tenantId().isBlank()))
                .highCardinalityKeyValue(OpenDispatchHttpObservationKeys.REQUEST_ID, context.requestId())
                .highCardinalityKeyValue(OpenDispatchHttpObservationKeys.CORRELATION_ID, context.correlationId())
                .highCardinalityKeyValue(OpenDispatchHttpObservationKeys.TENANT_ID, valueOrNone(context.tenantId()))
                .highCardinalityKeyValue(OpenDispatchHttpObservationKeys.OPERATOR_ID, context.operatorId())
                .highCardinalityKeyValue(OpenDispatchHttpObservationKeys.CLIENT_ADDRESS, context.clientAddress())
                .highCardinalityKeyValue(OpenDispatchHttpObservationKeys.USER_AGENT, context.userAgent());
    }

    private TenantResolution resolveTenantId(HttpServletRequest request) {
        String requestedTenant = safeIdentifier(
                firstNonBlank(request.getHeader(TENANT_ID_HEADER), request.getParameter("tenantId")), "");
        AuthenticatedWorkspace workspace = authenticatedWorkspace();
        if (!workspace.authoritative()) {
            return TenantResolution.resolved(requestedTenant);
        }
        if (workspace.instanceRoot()) {
            return TenantResolution.resolved(requestedTenant);
        }
        if (!requestedTenant.isBlank() && !requestedTenant.equals(workspace.tenantId())) {
            return TenantResolution.rejected();
        }
        return TenantResolution.resolved(workspace.tenantId());
    }

    private AuthenticatedWorkspace authenticatedWorkspace() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return AuthenticatedWorkspace.unresolved();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AdminPrincipal adminPrincipal) {
            return AuthenticatedWorkspace.authoritative(safeIdentifier(adminPrincipal.selectedTenantId(), ""));
        }
        if (principal instanceof MachineAuthenticationContext context) {
            TenantRef tenant = context.principal().activeTenant();
            String tenantId = tenant.scope() == TenantRef.Scope.TENANT ? tenant.tenantId() : "";
            return AuthenticatedWorkspace.authoritative(safeIdentifier(tenantId, ""));
        }
        if (principal instanceof AuthenticationContext context) {
            TenantRef tenant = context.activeTenant();
            if (context.subject().identityType()
                    == com.opensocket.aievent.core.iam.security.contract.SubjectRef.IdentityType.INSTANCE_ROOT) {
                return AuthenticatedWorkspace.root();
            }
            String tenantId = tenant.scope() == TenantRef.Scope.TENANT ? tenant.tenantId() : "";
            return AuthenticatedWorkspace.authoritative(safeIdentifier(tenantId, ""));
        }
        return AuthenticatedWorkspace.unresolved();
    }

    private String resolveOperatorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        if (authentication.getPrincipal() instanceof MachineAuthenticationContext context) {
            return safeIdentifier(context.principal().principalId(), "authenticated");
        }
        if (authentication.getPrincipal() instanceof AuthenticationContext context) {
            return safeIdentifier(context.principal().principalId(), "authenticated");
        }
        return safeIdentifier(authentication.getName(), "authenticated");
    }

    private String classifyRequest(String requestUri) {
        String uri = requestUri == null ? "" : requestUri;
        if (uri.startsWith("/actuator")) {
            return "actuator";
        }
        if (uri.startsWith("/internal")) {
            return "internal";
        }
        if (uri.startsWith("/admin")) {
            return "admin";
        }
        if (uri.startsWith("/api")) {
            return "api";
        }
        return "other";
    }

    private String safeIdentifier(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return SAFE_IDENTIFIER.matcher(trimmed).matches() ? trimmed : fallback;
    }

    private String bounded(String value, int maximumLength, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximumLength ? trimmed : trimmed.substring(0, maximumLength);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private record TenantResolution(String tenantId, boolean mismatch) {
        private static TenantResolution resolved(String tenantId) {
            return new TenantResolution(tenantId == null ? "" : tenantId, false);
        }

        private static TenantResolution rejected() {
            return new TenantResolution("", true);
        }
    }

    private record AuthenticatedWorkspace(String tenantId, boolean authoritative, boolean instanceRoot) {
        private static AuthenticatedWorkspace authoritative(String tenantId) {
            return new AuthenticatedWorkspace(tenantId == null ? "" : tenantId, true, false);
        }

        private static AuthenticatedWorkspace root() {
            return new AuthenticatedWorkspace("", true, true);
        }

        private static AuthenticatedWorkspace unresolved() {
            return new AuthenticatedWorkspace("", false, false);
        }
    }
}
