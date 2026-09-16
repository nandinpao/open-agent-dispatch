package com.opensocket.aievent.core.iam.runtime.security;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver;
import com.opensocket.aievent.core.iam.security.contract.*;
import com.opensocket.aievent.core.iam.token.application.command.ResolvePersonalAccessTokenTenantCommand;
import com.opensocket.aievent.core.iam.token.application.command.ValidateAccessTokenCommand;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import com.opensocket.aievent.core.iam.token.domain.TokenDomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Canonical human PAT HTTP adapter. Tenant authority comes from the PAT directory, never X-Tenant-Id. */
public final class IamPersonalAccessTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final String ADMIN_AUDIENCE = "OPEN_DISPATCH_ADMIN";
    private static final String RUNTIME_AUDIENCE = "OPEN_DISPATCH_RUNTIME";
    private static final String INTEGRATION_AUDIENCE = "OPEN_DISPATCH_INTEGRATION";
    private static final String AUDIT_AUDIENCE = "OPEN_DISPATCH_AUDIT";
    private final AccessTokenCommandPort tokens;
    private final TrustedClientIpResolver clientIp;
    private final Clock clock;

    public IamPersonalAccessTokenAuthenticationFilter(AccessTokenCommandPort tokens, TrustedClientIpResolver clientIp, Clock clock) {
        this.tokens=tokens; this.clientIp=clientIp; this.clock=clock;
    }

    public static boolean hasPatBearer(HttpServletRequest request) {
        String raw=bearer(request); return raw.startsWith("odp_pat_");
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !hasPatBearer(request); }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String correlation=correlation(request);
        request.setAttribute("opendispatch.correlationId",correlation);
        if (SecurityContextHolder.getContext().getAuthentication()!=null) {
            reject(response,correlation,400,"AUTH_MULTIPLE_CREDENTIALS","A browser session and Personal Access Token cannot be used on the same request."); return;
        }
        String raw=bearer(request);
        String path=request.getRequestURI();
        if (patForbiddenPath(path)) {
            reject(response,correlation,403,"AUTH_TOKEN_API_PREFIX_DENIED","Personal Access Tokens are not accepted on browser-session, bootstrap, OAuth, Event Intake, or internal trust endpoints."); return;
        }
        final com.opensocket.aievent.core.iam.token.application.result.ResolvedPersonalAccessTokenTenantResult target;
        try {
            target=tokens.resolvePersonalTenant(new ResolvePersonalAccessTokenTenantCommand(raw));
        } catch (TokenDomainException ex) {
            rejectTokenFailure(response,correlation,ex); return;
        }
        try (IamTenantContextHolder.Scope ignored=IamTenantContextHolder.open(new IamTenantExecutionContext(target.tenantId(),"pat-authentication"))) {
            final com.opensocket.aievent.core.iam.token.application.result.ValidatedTokenResult validated;
            try {
                validated=tokens.validate(new ValidateAccessTokenCommand(raw,target.tenantId(),audienceFor(path),path,clientIp.resolve(request),correlation));
            } catch (TokenDomainException ex) {
                rejectTokenFailure(response,correlation,ex); return;
            }
            if(validated.type()!=AccessTokenType.PERSONAL_ACCESS_TOKEN || validated.principal().principalType()!=PrincipalRef.PrincipalType.USER) {
                reject(response,correlation,401,"AUTH_TOKEN_TYPE_MISMATCH","A USER Personal Access Token is required."); return;
            }
            var now=clock.instant();
            AuthenticationContext context=new AuthenticationContext(
                    new SubjectRef(SubjectRef.IdentityType.HUMAN_USER,validated.principal().principalId()),
                    validated.principal(), TenantRef.tenant(validated.tenantId()), Optional.empty(),
                    new AuthenticationAssurance(AuthenticationAssurance.Level.TOKEN,Set.of("PERSONAL_ACCESS_TOKEN"),now),
                    validated.securityEpoch(),Optional.empty(),validated.issuedAt(),validated.expiresAt());
            Set<String> boundary=validated.effectiveScope().permissions();
            SecurityContextHolder.getContext().setAuthentication(new IamRuntimeAuthenticationToken(
                    context,List.of(),boundary,validated.tokenId(),"PERSONAL_ACCESS_TOKEN"));
            request.setAttribute(IamApiRequestContextFactory.CREDENTIAL_PERMISSION_BOUNDARY_ATTRIBUTE,boundary);
            request.setAttribute("opendispatch.authenticationMethod","PERSONAL_ACCESS_TOKEN");
            request.setAttribute("opendispatch.credentialId",validated.tokenId());
            request.setAttribute("opendispatch.authoritativeTenantId",validated.tenantId());
            // Downstream failures are deliberately not translated into token-authentication failures.
            chain.doFilter(request,response);
        }
    }

    private static void rejectTokenFailure(HttpServletResponse response,String correlation,TokenDomainException ex)throws IOException {
        int status=switch(ex.reasonCode().name()){case "AUTH_TOKEN_RATE_LIMITED"->429;case "AUTH_TOKEN_AUDIENCE_DENIED","AUTH_TOKEN_API_PREFIX_DENIED","AUTH_TOKEN_CIDR_DENIED","AUTH_TOKEN_SCOPE_INSUFFICIENT","AUTH_TOKEN_TENANT_MISMATCH"->403;default->401;};
        reject(response,correlation,status,ex.reasonCode().name(),ex.getMessage());
    }


    static boolean patForbiddenPath(String path) {
        if(path==null) return true;
        return path.startsWith("/api/session") || path.startsWith("/api/bootstrap") || path.startsWith("/api/events/")
                || path.startsWith("/oauth") || path.startsWith("/internal") || path.startsWith("/actuator");
    }

    static String audienceFor(String path) {
        String p=path==null?"":path;
        if(p.startsWith("/api/security-events") || p.contains("/audit") || p.contains("/security-summary")) return AUDIT_AUDIENCE;
        if(p.startsWith("/api/integrations")) return INTEGRATION_AUDIENCE;
        if(p.startsWith("/api/tasks") || p.startsWith("/api/dispatch") || p.startsWith("/api/a2a") || p.startsWith("/api/admin/tasks")) return RUNTIME_AUDIENCE;
        return ADMIN_AUDIENCE;
    }

    private static String bearer(HttpServletRequest request){String h=request.getHeader("Authorization");return h!=null&&h.regionMatches(true,0,"Bearer ",0,7)?h.substring(7).trim():"";}
    private static String correlation(HttpServletRequest request){String v=request.getHeader("X-Correlation-Id");if(v==null||v.isBlank())v=request.getHeader("X-Request-Id");return v==null||v.isBlank()?UUID.randomUUID().toString():v.trim();}
    private static void reject(HttpServletResponse response,String correlation,int status,String code,String message)throws IOException{response.setStatus(status);response.setContentType(MediaType.APPLICATION_JSON_VALUE);response.setHeader("X-Correlation-Id",correlation);if(status==401)response.setHeader("WWW-Authenticate","Bearer realm=\"OpenDispatch Personal Access Token\", error=\"invalid_token\"");response.getWriter().write("{\"code\":\""+escape(code)+"\",\"error_code\":\""+escape(code)+"\",\"message\":\""+escape(message)+"\",\"correlationId\":\""+escape(correlation)+"\"}");}
    private static String escape(String v){return v==null?"":v.replace("\\","\\\\").replace("\"","\\\"");}
}
