package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.integration.identity.IntegrationOperation;
import com.opensocket.aievent.core.integration.identity.IntegrationResourceScope;
import com.opensocket.aievent.core.issuetracking.identity.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.*; import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Trusted P4RA-F composition boundary. Resource Access authorizes the Human; Issue Tracking then resolves Provider identity. */
@Component
@ConditionalOnProperty(prefix="resource-access",name={"enabled","integration-enabled"},havingValue="true")
public final class IntegrationResourceAccessCoordinator {
 private final ResourceAccessEnforcementPort enforcement; private final IntegrationScopeQueryPlanPort plans;
 private final IntegrationScopeQueryAuditPort audits; private final ExternalWriteAuthorizationPort externalWrites;
 private final ProviderWriteIdentityPort providerIdentities; private final ProviderExecutionAttributionRepository attributions;
 private final ResourceEnforcementContextPort contexts;
 public IntegrationResourceAccessCoordinator(ResourceAccessEnforcementPort enforcement,IntegrationScopeQueryPlanPort plans,
  Optional<IntegrationScopeQueryAuditPort> audits,ExternalWriteAuthorizationPort externalWrites,ProviderWriteIdentityPort providerIdentities,
  ProviderExecutionAttributionRepository attributions,ResourceEnforcementContextPort contexts){
  this.enforcement=Objects.requireNonNull(enforcement);this.plans=Objects.requireNonNull(plans);this.audits=audits.orElse(null);
  this.externalWrites=Objects.requireNonNull(externalWrites);this.providerIdentities=Objects.requireNonNull(providerIdentities);
  this.attributions=Objects.requireNonNull(attributions);this.contexts=Objects.requireNonNull(contexts);
 }
 public AuthorizationDecision authorize(ResourceType type,String id,String permission,ResourceAction.ActionKind kind,boolean sideEffecting,VisibilityLevel visibility,String purpose){
  var runtime=contexts.current();return enforcement.authorize(new ResourceEnforcementCommand(new ResourceAction(permission,kind,sideEffecting),
   new ResourceRef(runtime.authentication().activeTenant().tenantId(),type,req(id,"resourceId")),visibility,RequestChannel.REST,req(purpose,"purpose"),
   sideEffecting?OperationPhase.BEFORE_SIDE_EFFECT:OperationPhase.START,SecurityEpoch.ZERO,Map.of("p4raPhase","P4RA-F")));
 }
 public IntegrationResourceScope scope(String permission,ResourceType type,VisibilityLevel visibility,String purpose){
  IntegrationScopeQueryPlan p=plans.build(permission,type,visibility,purpose);return new IntegrationResourceScope(p.tenantId(),p.principalType(),p.principalId(),p.permissionCode(),p.resourceType().name(),p.denyAll(),p.tenantWide(),p.exactDepartmentIds(),p.subtreeDepartmentRootIds(),p.groupIds(),p.explicitResourceIds(),p.excludedResourceIds(),p.deniedDepartmentIds(),p.deniedSubtreeDepartmentRootIds(),p.deniedGroupIds(),p.maximumVisibility().name(),p.planHash());
 }
 public IntegrationScopeQueryPlan plan(String permission,ResourceType type,VisibilityLevel visibility,String purpose){return plans.build(permission,type,visibility,purpose);}
 public IntegrationResourceScope toScope(IntegrationScopeQueryPlan p){return new IntegrationResourceScope(p.tenantId(),p.principalType(),p.principalId(),p.permissionCode(),p.resourceType().name(),p.denyAll(),p.tenantWide(),p.exactDepartmentIds(),p.subtreeDepartmentRootIds(),p.groupIds(),p.explicitResourceIds(),p.excludedResourceIds(),p.deniedDepartmentIds(),p.deniedSubtreeDepartmentRootIds(),p.deniedGroupIds(),p.maximumVisibility().name(),p.planHash());}
 public void shadowMismatch(IntegrationScopeQueryPlan p,String purpose,Set<String> legacyOnly,Set<String> scopedOnly){if(audits!=null&&!legacyOnly.equals(scopedOnly))audits.recordShadowMismatch(p,purpose,legacyOnly,scopedOnly,Instant.now());}
 public ExternalWriteExecutionAuthorization authorizeExternalWrite(ResourceType type,String resourceId,String permission,ResourceAction.ActionKind kind,
  VisibilityLevel visibility,String purpose,String mappingId,IntegrationOperation operation,String idempotencyKey,boolean sodSatisfied,boolean requireStepUp){
  var runtime=contexts.current();String tenant=runtime.authentication().activeTenant().tenantId();
  var assurance=runtime.authentication().assurance().level();
  boolean stepUpSatisfied=!requireStepUp
    ||assurance==com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance.Level.MFA
    ||assurance==com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance.Level.SYSTEM;
  ExternalWriteAuthorizationContext human=externalWrites.authorizeHumanWrite(new ExternalWriteAuthorizationCommand(new ResourceRef(tenant,type,req(resourceId,"resourceId")),
   new ResourceAction(permission,kind,true),visibility,purpose,sodSatisfied,stepUpSatisfied,req(idempotencyKey,"idempotencyKey"),Map.of("mappingId",req(mappingId,"mappingId"),"operation",operation.name(),"stepUpRequired",Boolean.toString(requireStepUp))));
  String blockingReason=human.auditEvidence().get("blockingReason");
  boolean shadowAllow="SHADOW".equals(human.auditEvidence().get("decisionMode"))&&"ALLOW".equals(human.auditEvidence().get("decisionEffect"));
  if(!human.executable()&&!shadowAllow)throw new IllegalStateException(blockingReason==null?"EXTERNAL_WRITE_AUTHORIZATION_NOT_EXECUTABLE":blockingReason);
  ProviderWriteIdentityResolution provider=providerIdentities.resolve(tenant,human.humanPrincipal().principalId(),mappingId,operation,OffsetDateTime.now());
  String attributionId=UUID.randomUUID().toString();ProviderExecutionAttributionStatus status=human.executable()?ProviderExecutionAttributionStatus.AUTHORIZED:ProviderExecutionAttributionStatus.SHADOW_OBSERVED;
  ProviderExecutionAttribution evidence=new ProviderExecutionAttribution(tenant,attributionId,human.humanPrincipal().principalId(),human.authorizationDecisionId(),type.name(),resourceId,permission,purpose,provider.connectionId(),provider.mappingId(),provider.integrationPrincipalId(),provider.credentialId(),provider.credentialVersion(),provider.providerActorId(),provider.policy(),status,human.correlationId(),idempotencyKey,"","",OffsetDateTime.now());
  ProviderExecutionAttribution stored=attributions.findByIdempotencyKey(tenant,idempotencyKey).orElseGet(()->attributions.append(evidence));
  return new ExternalWriteExecutionAuthorization(human,provider,stored.attributionId());
 }
 private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v.trim();}
}
