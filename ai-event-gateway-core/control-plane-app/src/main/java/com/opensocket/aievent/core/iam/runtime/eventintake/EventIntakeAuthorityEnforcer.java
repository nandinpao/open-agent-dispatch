package com.opensocket.aievent.core.iam.runtime.eventintake;

import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.runtime.config.EventIntakeSecurityProperties;
import com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.security.incident.RuntimeIncidentControlPolicy;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineResourceAccessAuditPort;
import com.opensocket.aievent.core.security.audit.SecurityAuditFailureReporter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Validates body compatibility against JWT-derived authority and emits creation-time authorization evidence. */
public final class EventIntakeAuthorityEnforcer {
    private final EventIntakeSecurityProperties properties;
    private final MachineResourceAccessAuditPort audit;
    private final Clock clock;
    private final TrustedClientIpResolver clientIp;
    private final RuntimeIncidentControlPolicy incidentControls;
    private final SecurityAuditFailureReporter auditFailures;

    public EventIntakeAuthorityEnforcer(EventIntakeSecurityProperties properties,MachineResourceAccessAuditPort audit,Clock clock,TrustedClientIpResolver clientIp,RuntimeIncidentControlPolicy incidentControls){
        this(properties,audit,clock,clientIp,incidentControls,SecurityAuditFailureReporter.noop());
    }
    public EventIntakeAuthorityEnforcer(EventIntakeSecurityProperties properties,MachineResourceAccessAuditPort audit,Clock clock,TrustedClientIpResolver clientIp,RuntimeIncidentControlPolicy incidentControls,SecurityAuditFailureReporter auditFailures){this.properties=properties;this.audit=audit;this.clock=clock;this.clientIp=clientIp;this.incidentControls=incidentControls;this.auditFailures=auditFailures==null?SecurityAuditFailureReporter.noop():auditFailures;}

    public EventIntakeAuthorizationEvidence authorize(EventIntakeRequest body,HttpServletRequest request){
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        MachineAuthenticationContext jwt=auth!=null&&auth.getPrincipal() instanceof MachineAuthenticationContext m?m:null;
        MachineAuthenticationContext shadow=request.getAttribute(EventIntakeMachineAuthenticationFilter.SHADOW_CONTEXT_ATTRIBUTE) instanceof MachineAuthenticationContext m?m:null;
        String correlation=correlation(request);
        String decisionId="authz-event-intake-"+UUID.randomUUID();

        if(jwt!=null){
            try {
                validateBody(body,jwt);
                append(decisionId,request,"JWT","ALLOW","MACHINE_EVENT_INTAKE_ALLOWED",jwt,body.getSourceSystem(),correlation);
                return new EventIntakeAuthorizationEvidence(decisionId,"JWT",jwt);
            } catch (EventIntakeResourceAuthorizationException e) {
                append(decisionId,request,"JWT","DENY",e.reasonCode(),jwt,body.getSourceSystem(),correlation);
                throw e;
            }
        }
        if(properties.jwtRequired()) throw EventIntakeResourceAuthorizationException.unauthorized("MACHINE_EVENT_INTAKE_AUTHENTICATION_REQUIRED","A machine Bearer access token is required.");
        if(!incidentControls.sourceSystemIngressAllowed(body.getTenantId(),body.getSourceSystem()))
            throw EventIntakeResourceAuthorizationException.forbidden("MACHINE_SOURCE_SYSTEM_QUARANTINED","Source System is not ACTIVE or is quarantined by Security Incident control.");

        if(properties.shadowOnly()){
            String shadowError=(String)request.getAttribute(EventIntakeMachineAuthenticationFilter.SHADOW_ERROR_ATTRIBUTE);
            if(shadow!=null){
                try{validateBody(body,shadow);append(decisionId,request,"JWT_SHADOW","ALLOW","MACHINE_EVENT_INTAKE_SHADOW_MATCH",shadow,body.getSourceSystem(),correlation);}
                catch(EventIntakeResourceAuthorizationException e){append(decisionId,request,"JWT_SHADOW","DENY",e.reasonCode(),shadow,body.getSourceSystem(),correlation);}
            }else if(shadowError!=null){append(decisionId,request,"JWT_SHADOW","DENY",shadowError,null,body.getSourceSystem(),correlation);}
        }
        append(decisionId,request,"LEGACY","ALLOW","MACHINE_EVENT_INTAKE_LEGACY_ACCEPTED",null,body.getSourceSystem(),correlation);
        return new EventIntakeAuthorizationEvidence(decisionId,"LEGACY",null);
    }

    private void validateBody(EventIntakeRequest body,MachineAuthenticationContext context){
        String tenant=context.principal().activeTenant().tenantId();
        if(body.getTenantId()==null||!tenant.equals(body.getTenantId().trim())) throw EventIntakeResourceAuthorizationException.forbidden("MACHINE_TENANT_CONTEXT_MISMATCH","Event Tenant does not match the authenticated machine Tenant.");
        if(body.getSourceSystem()==null||!context.accessBoundary().permitsSourceSystem(body.getSourceSystem())) throw EventIntakeResourceAuthorizationException.forbidden("MACHINE_SOURCE_SYSTEM_DENIED","Source System is outside the authenticated machine boundary.");
        if(!incidentControls.sourceSystemIngressAllowed(tenant,body.getSourceSystem())) throw EventIntakeResourceAuthorizationException.forbidden("MACHINE_SOURCE_SYSTEM_QUARANTINED","Source System is not ACTIVE or is quarantined by Security Incident control.");
    }

    private void append(String decisionId,HttpServletRequest request,String method,String outcome,String reason,MachineAuthenticationContext c,String sourceSystem,String correlation){
        if(!properties.isAuditEnabled())return;
        try{audit.append(new MachineResourceAccessAuditPort.Event(decisionId,"/api/events/intake",request.getMethod(),properties.getMode().name(),method,outcome,reason,
                c==null?"":c.principal().activeTenant().tenantId(),c==null?"":c.principal().principalId(),c==null?"":c.credential().credentialId(),c==null?"":c.credential().tokenId(),sourceSystem==null?"":sourceSystem,clientIp.resolve(request),correlation,clock.instant()));}
        catch(RuntimeException failure){auditFailures.report("EVENT_INTAKE_AUTHORIZATION","MACHINE_RESOURCE_ACCESS",c==null?null:c.principal().activeTenant().tenantId(),c==null?null:c.principal().principalId(),correlation,reason,failure);}
    }
    private static String correlation(HttpServletRequest r){
        String context=OpenDispatchRequestContextHolder.current().map(value->value.correlationId()).orElse(null);
        if(context!=null&&!context.isBlank())return context.trim();
        String v=r==null?null:r.getHeader("X-Correlation-Id");
        return v==null||v.isBlank()?"event-intake-"+UUID.randomUUID():v.trim();
    }
}
