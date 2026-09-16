package com.opensocket.aievent.core.iam.runtime.eventintake;

import com.opensocket.aievent.core.iam.runtime.config.EventIntakeSecurityProperties;
import com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineResourceAccessAuditPort;
import com.opensocket.aievent.core.security.audit.SecurityAuditFailureReporter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Phase 9 JWT authentication layer for POST /api/events/intake.
 * In SHADOW mode JWT validation never grants authority; the legacy credential remains authoritative.
 */
public final class EventIntakeMachineAuthenticationFilter extends OncePerRequestFilter {
    public static final String JWT_CONTEXT_ATTRIBUTE = EventIntakeMachineAuthenticationFilter.class.getName()+".jwtContext";
    public static final String SHADOW_CONTEXT_ATTRIBUTE = EventIntakeMachineAuthenticationFilter.class.getName()+".shadowContext";
    public static final String SHADOW_ERROR_ATTRIBUTE = EventIntakeMachineAuthenticationFilter.class.getName()+".shadowError";
    public static final String AUTH_METHOD_ATTRIBUTE = EventIntakeMachineAuthenticationFilter.class.getName()+".authMethod";

    private final EventIntakeMachineResourceAuthorizer authorizer;
    private final EventIntakeSecurityProperties properties;
    private final MachineResourceAccessAuditPort audit;
    private final Clock clock;
    private final TrustedClientIpResolver clientIp;
    private final SecurityAuditFailureReporter auditFailures;

    public EventIntakeMachineAuthenticationFilter(EventIntakeMachineResourceAuthorizer authorizer,
            EventIntakeSecurityProperties properties, MachineResourceAccessAuditPort audit, Clock clock, TrustedClientIpResolver clientIp) {
        this(authorizer,properties,audit,clock,clientIp,SecurityAuditFailureReporter.noop());
    }

    public EventIntakeMachineAuthenticationFilter(EventIntakeMachineResourceAuthorizer authorizer,
            EventIntakeSecurityProperties properties, MachineResourceAccessAuditPort audit, Clock clock,
            TrustedClientIpResolver clientIp, SecurityAuditFailureReporter auditFailures) {
        this.authorizer=authorizer; this.properties=properties; this.audit=audit; this.clock=clock; this.clientIp=clientIp;
        this.auditFailures=auditFailures==null?SecurityAuditFailureReporter.noop():auditFailures;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/api/events/intake".equals(request.getRequestURI()));
    }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String bearer = bearer(request);
        if (properties.getMode() == EventIntakeSecurityProperties.Mode.LEGACY_ONLY) {
            request.setAttribute(AUTH_METHOD_ATTRIBUTE,"LEGACY");
            chain.doFilter(request,response); return;
        }
        if (bearer.isBlank()) {
            if (properties.jwtRequired()) { reject(request,response,401,"MACHINE_EVENT_INTAKE_AUTHENTICATION_REQUIRED","A machine Bearer access token is required."); return; }
            request.setAttribute(AUTH_METHOD_ATTRIBUTE,"LEGACY");
            chain.doFilter(request,response); return;
        }

        try {
            MachineAuthenticationContext context=authorizer.authorize(bearer,request.getRequestURI(),clientIp.resolve(request));
            if (properties.shadowOnly()) {
                request.setAttribute(SHADOW_CONTEXT_ATTRIBUTE,context);
                request.setAttribute(AUTH_METHOD_ATTRIBUTE,"LEGACY_SHADOW_JWT");
                chain.doFilter(request,response); return;
            }
            request.setAttribute(JWT_CONTEXT_ATTRIBUTE,context);
            request.setAttribute(AUTH_METHOD_ATTRIBUTE,"JWT");
            SecurityContextHolder.getContext().setAuthentication(new EventIntakeMachineAuthenticationToken(context));
            chain.doFilter(request,response);
        } catch (EventIntakeResourceAuthorizationException e) {
            if (properties.shadowOnly()) {
                request.setAttribute(SHADOW_ERROR_ATTRIBUTE,e.reasonCode());
                request.setAttribute(AUTH_METHOD_ATTRIBUTE,"LEGACY_SHADOW_JWT_DENY");
                chain.doFilter(request,response); return;
            }
            reject(request,response,e.httpStatus(),e.reasonCode(),e.getMessage());
        }
    }

    private void reject(HttpServletRequest request,HttpServletResponse response,int status,String code,String message)throws IOException {
        String correlation=correlation(request);
        appendAudit(request,"JWT","DENY",code,"","","","",correlation);
        response.setStatus(status); response.setCharacterEncoding(StandardCharsets.UTF_8.name()); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Correlation-Id",correlation);
        if(status==401) response.setHeader("WWW-Authenticate","Bearer realm=\"opendispatch-event-api\", error=\"invalid_token\"");
        response.getWriter().write("{\"code\":\""+escape(code)+"\",\"error_code\":\""+escape(code)+"\",\"message\":\""+escape(message)+"\",\"correlationId\":\""+escape(correlation)+"\"}");
    }

    public void appendAudit(HttpServletRequest request,String authMethod,String outcome,String reason,String tenant,String account,String credential,String jwtId,String correlation){
        if(!properties.isAuditEnabled())return;
        try{audit.append(new MachineResourceAccessAuditPort.Event(UUID.randomUUID().toString(),"/api/events/intake",request.getMethod(),properties.getMode().name(),authMethod,outcome,reason,tenant,account,credential,jwtId,"",clientIp.resolve(request),correlation,clock.instant()));}
        catch(RuntimeException failure){auditFailures.report("EVENT_INTAKE_AUTHENTICATION","MACHINE_RESOURCE_ACCESS",tenant,account,correlation,reason,failure);}
    }
    private static String bearer(HttpServletRequest r){String h=r.getHeader("Authorization");return h!=null&&h.regionMatches(true,0,"Bearer ",0,7)?h.substring(7).trim():"";}
    private static String correlation(HttpServletRequest r){String v=r.getHeader("X-Correlation-Id");return v==null||v.isBlank()?"event-intake-"+UUID.randomUUID():v.trim();}
    private static String escape(String v){return v==null?"":v.replace("\\","\\\\").replace("\"","\\\"");}
}
