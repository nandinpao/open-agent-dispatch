package com.opensocket.aievent.core.http.context;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.opensocket.aievent.core.http.observation.OpenDispatchHttpObservationKeys;
import com.opensocket.aievent.core.identity.AdminPrincipal;

/**
 * Establishes request diagnostics after authentication. Spring owns the HTTP root observation;
 * this filter only enriches that observation and manages request-local state.
 */
public class OpenDispatchRequestContextFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}");
    private static final int MAX_USER_AGENT_LENGTH = 256;
    private static final int MAX_CLIENT_ADDRESS_LENGTH = 128;

    private final ObservationRegistry observationRegistry;

    public OpenDispatchRequestContextFilter(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = safeIdentifier(request.getHeader(REQUEST_ID_HEADER), UUID.randomUUID().toString());
        String correlationId = safeIdentifier(request.getHeader(CORRELATION_ID_HEADER), requestId);
        String tenantId = resolveTenantId(request);
        String operatorId = resolveOperatorId();
        String clientAddress = bounded(request.getRemoteAddr(), MAX_CLIENT_ADDRESS_LENGTH, "unknown");
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
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try (OpenDispatchRequestContextHolder.Scope ignored = OpenDispatchRequestContextHolder.open(context);
             MdcContextScope ignoredMdc = MdcContextScope.open(mdc)) {
            filterChain.doFilter(request, response);
        }
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


    private String resolveTenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AdminPrincipal principal) {
            return safeIdentifier(principal.selectedTenantId(), "");
        }
        return safeIdentifier(firstNonBlank(request.getHeader(TENANT_ID_HEADER), request.getParameter("tenantId")), "");
    }

    private String resolveOperatorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return "anonymous";
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
}
