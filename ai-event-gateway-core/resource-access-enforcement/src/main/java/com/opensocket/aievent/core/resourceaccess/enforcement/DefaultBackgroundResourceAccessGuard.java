package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/** Background work never receives implicit System Admin. Every resource/action is explicitly evaluated and lease-bound. */
@Component
@ConditionalOnProperty(prefix="resource-access",name="background-enabled",havingValue="true")
public final class DefaultBackgroundResourceAccessGuard implements BackgroundResourceAccessGuardPort {
 private final ResourceAuthorizationPort authorization;private final RuntimeAuthorizationLeasePort leases;private final BackgroundAuthorizationAuditPort audit;private final Clock clock;
 public DefaultBackgroundResourceAccessGuard(ResourceAuthorizationPort authorization,RuntimeAuthorizationLeasePort leases,BackgroundAuthorizationAuditPort audit,Clock resourceAccessClock){this.authorization=Objects.requireNonNull(authorization);this.leases=Objects.requireNonNull(leases);this.audit=Objects.requireNonNull(audit);this.clock=Objects.requireNonNull(resourceAccessClock);}
 @Override public BackgroundJobAuthorization authorize(BackgroundJobAuthorizationCommand command){
  List<String> decisions=new ArrayList<>();List<ResourceRef> refs=new ArrayList<>();List<RuntimeAuthorizationLease> runtimeLeases=new ArrayList<>();boolean executable=true;
  try{
   for(BackgroundJobResourceRequest item:command.resources()){
    if(!item.resourceRef().tenantId().equals(command.serviceAuthentication().activeTenant().tenantId()))throw new IllegalArgumentException("Background resource tenant mismatch");
    AuthorizationRequest request=new AuthorizationRequest(command.serviceAuthentication().principal(),command.serviceAuthentication(),command.serviceAuthentication().activeTenant(),item.action(),item.resourceRef(),item.requestedVisibility(),RequestChannel.BACKGROUND_JOB,command.purpose(),OperationPhase.START,"",command.correlationId(),SecurityEpoch.ZERO,Map.of("jobCode",command.jobCode()));
    AuthorizationDecision decision=authorization.evaluate(request);decisions.add(decision.decisionId());refs.add(item.resourceRef());boolean allowed=decision.mode()==AuthorizationDecisionMode.FORMAL&&decision.effect()==DecisionEffect.ALLOW&&!decision.shadowOnly();executable&=allowed;if(allowed)runtimeLeases.add(leases.issue(request,decision));
   }
   if(!executable)runtimeLeases=revokeAll(runtimeLeases,"BACKGROUND_AGGREGATE_NOT_EXECUTABLE",command.correlationId());
   BackgroundJobAuthorization result=new BackgroundJobAuthorization("background-auth-"+UUID.randomUUID(),command.jobCode(),command.serviceAuthentication().principal().principalId(),decisions,refs,runtimeLeases,clock.instant(),executable);audit.append(result,command.purpose(),command.correlationId());return result;
  }catch(RuntimeException ex){revokeAll(runtimeLeases,"BACKGROUND_AUTHORIZATION_ABORTED",command.correlationId());throw ex;}
 }
 private List<RuntimeAuthorizationLease> revokeAll(List<RuntimeAuthorizationLease> values,String reason,String correlation){List<RuntimeAuthorizationLease> out=new ArrayList<>();for(RuntimeAuthorizationLease l:values){try{out.add(leases.revoke(l.resourceRef().tenantId(),l.leaseId(),reason,correlation));}catch(RuntimeException ignored){out.add(l);}}return List.copyOf(out);}
}
