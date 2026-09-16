package com.opensocket.aievent.core.iam.api.security;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RbacEventPublisher;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.rbac.event.RbacSecurityEvent;
import com.opensocket.aievent.core.iam.security.contract.*;
import java.time.*;
import java.util.*;

/** Enforces audited tenant administration and R7 Root break-glass guarantees for INSTANCE-scoped sensitive writes. */
public final class R7SensitiveOperationGuard {
    private static final Duration ROOT_REAUTH_WINDOW=Duration.ofMinutes(2);
    private static final Duration ROOT_MAX_SESSION=Duration.ofMinutes(30);
    private final RbacEventPublisher events;private final Clock clock;
    public R7SensitiveOperationGuard(RbacEventPublisher events,Clock clock){this.events=Objects.requireNonNull(events);this.clock=Objects.requireNonNull(clock);}
    public void require(IamApiRequestContext context){
        context.requireIdempotencyKey();String reason=context.requireAuditReason();AuthenticationContext auth=context.requireAuthentication();
        if(auth.principal().principalType()!=PrincipalRef.PrincipalType.INSTANCE_ROOT)return;
        if(reason.length()<12)throw IamApiException.badRequest("IAM_AUDIT_REASON_TOO_SHORT","Root audit reason must explain the action using at least twelve characters");
        Instant now=clock.instant();
        if(auth.activeTenant().scope()==TenantRef.Scope.TENANT){
            events.publish(new RbacSecurityEvent(UUID.randomUUID().toString(),"ROOT_TENANT_ADMIN_WRITE",activeTenant(auth),context.actorId(),auth.principal().principalId(),"","","",
                    Map.of("correlationId",context.correlationId(),"sessionId",auth.session().map(SessionRef::sessionId).orElse(""),"auditReason",reason),now));
            return;
        }
        AuthenticationAssurance assurance=auth.assurance();
        if(assurance.level()!=AuthenticationAssurance.Level.MFA&&assurance.level()!=AuthenticationAssurance.Level.RECOVERY&&assurance.level()!=AuthenticationAssurance.Level.SYSTEM)
            reject(context,RbacReasonCode.RBAC_ROOT_MFA_REQUIRED,"Root sensitive writes require MFA or recovery assurance");
        if(assurance.authenticatedAt().plus(ROOT_REAUTH_WINDOW).isBefore(now))
            reject(context,RbacReasonCode.RBAC_ROOT_STEP_UP_REQUIRED,"Root step-up authentication is older than two minutes");
        if(Duration.between(auth.issuedAt(),auth.expiresAt()).compareTo(ROOT_MAX_SESSION)>0)
            reject(context,RbacReasonCode.RBAC_ROOT_SESSION_TOO_LONG,"Root break-glass session exceeds thirty minutes");
        events.publish(new RbacSecurityEvent(UUID.randomUUID().toString(),"ROOT_BREAK_GLASS_SENSITIVE_WRITE",activeTenant(auth),context.actorId(),auth.principal().principalId(),"","","",
                Map.of("correlationId",context.correlationId(),"sessionId",auth.session().map(SessionRef::sessionId).orElse(""),"methods",String.join(",",assurance.methods()),"auditReason",reason),now));
    }
    private void reject(IamApiRequestContext c,RbacReasonCode code,String message){events.publish(new RbacSecurityEvent(UUID.randomUUID().toString(),"ROOT_BREAK_GLASS_REJECTED",activeTenant(c.requireAuthentication()),c.actorId(),c.requireAuthentication().principal().principalId(),"","",code.name(),Map.of("correlationId",c.correlationId()),clock.instant()));throw new RbacDomainException(code,message);}
    private static String activeTenant(AuthenticationContext a){return a.activeTenant().scope()==TenantRef.Scope.TENANT?a.activeTenant().tenantId():"";}
}
