package com.opensocket.aievent.core.resourceaccess.core;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;import java.util.*;
/** Deterministic scope matching. Unsupported scope semantics fail closed. */
public final class ResourceScopeMatcher {
 private final ResourceDecisionEvidenceRepository evidence;
 public ResourceScopeMatcher(ResourceDecisionEvidenceRepository evidence){this.evidence=Objects.requireNonNull(evidence);}
 public boolean matches(ScopeType type,String ref,ResourceDescriptor descriptor,List<ResourceParticipantProjection> participants,PrincipalRef principal,PrincipalScopeSnapshot scopes,Instant at){
  String n=ref==null?"":ref.trim();ResourceRef resource=descriptor.resourceRef();OwnershipDescriptor o=descriptor.ownership();
  return switch(type){
   case TENANT->n.equals(resource.tenantId());
   case DEPARTMENT->relevantDepartments(o).contains(n);
   case DEPARTMENT_SUBTREE->relevantDepartments(o).stream().anyMatch(d->evidence.departmentContains(resource.tenantId(),n,d));
   case GROUP->!n.isEmpty()&&n.equals(o.ownerGroupId());
   case RESOURCE->n.equals(resource.resourceId());
   case RESOURCE_TREE->matchesTree(n,descriptor);
   case TASK_CHAIN->matchesTaskChain(n,descriptor);
   case PARTICIPANT->participantMatch(participants,principal,scopes,at,n);
   case OWNER->ownerMatch(o,principal,scopes);
   case ASSIGNED_TO_ME->participantMatch(participants,principal,scopes,at,n);
   case CREATED_BY_ME,AUDIT_WINDOW,EXPLICIT_SET->false;
  };
 }
 public boolean ownerMatch(OwnershipDescriptor o,PrincipalRef principal,PrincipalScopeSnapshot scopes){
  if(principal.principalType()==PrincipalRef.PrincipalType.USER&&!o.stewardUserId().isEmpty()&&o.stewardUserId().equals(principal.principalId()))return true;
  if((principal.principalType()==PrincipalRef.PrincipalType.SERVICE_ACCOUNT||principal.principalType()==PrincipalRef.PrincipalType.SYSTEM_SERVICE)&&!o.custodianServiceId().isEmpty()&&o.custodianServiceId().equals(principal.principalId()))return true;
  return (!o.ownerDepartmentId().isEmpty()&&scopes.departmentIds().contains(o.ownerDepartmentId()))||(!o.ownerGroupId().isEmpty()&&scopes.groupIds().contains(o.ownerGroupId()));
 }
 public boolean participantMatch(List<ResourceParticipantProjection> participants,PrincipalRef principal,PrincipalScopeSnapshot scopes,Instant at,String requiredRef){
  return participants.stream().anyMatch(p->effective(p,at)&&matchesPrincipal(p,principal,scopes)&&(requiredRef==null||requiredRef.isBlank()||requiredRef.equals(p.participantRefId())));
 }
 public List<ResourceParticipantProjection> matchingParticipants(List<ResourceParticipantProjection> participants,PrincipalRef principal,PrincipalScopeSnapshot scopes,String permission,Instant at){
  return participants.stream().filter(p->effective(p,at)).filter(p->permissionScopeCandidate(p,principal,scopes)).filter(p->p.allowedPermissionCodes().isEmpty()||p.allowedPermissionCodes().contains(permission)).toList();
 }
 /** Organization participants are resource evidence, not membership authority. IAM permission scope is checked later. */
 private boolean permissionScopeCandidate(ResourceParticipantProjection p,PrincipalRef principal,PrincipalScopeSnapshot scopes){return switch(p.participantType()){
  case DEPARTMENT,GROUP->true;
  default->matchesPrincipal(p,principal,scopes);};}
 private boolean matchesPrincipal(ResourceParticipantProjection p,PrincipalRef principal,PrincipalScopeSnapshot scopes){return switch(p.participantType()){
  case USER->principal.principalType()==PrincipalRef.PrincipalType.USER&&p.participantRefId().equals(principal.principalId());
  case SERVICE_ACCOUNT->(principal.principalType()==PrincipalRef.PrincipalType.SERVICE_ACCOUNT||principal.principalType()==PrincipalRef.PrincipalType.SYSTEM_SERVICE)&&p.participantRefId().equals(principal.principalId());
  case DEPARTMENT->scopes.departmentIds().contains(p.participantRefId());
  case GROUP->scopes.groupIds().contains(p.participantRefId());
  case AGENT_ASSIGNMENT->(principal.principalType()==PrincipalRef.PrincipalType.AGENT||principal.principalType()==PrincipalRef.PrincipalType.A2A_AGENT)&&p.participantRefId().equals(principal.principalId());};}
 private boolean effective(ResourceParticipantProjection p,Instant at){return p.status()==ResourceParticipantStatus.ACTIVE&&!at.isBefore(p.validFrom())&&(p.validTo()==null||at.isBefore(p.validTo()));}
 private Set<String> relevantDepartments(OwnershipDescriptor o){Set<String>s=new LinkedHashSet<>();add(s,o.ownerDepartmentId());add(s,o.requesterDepartmentId());add(s,o.executorDepartmentId());return s;}
 private boolean matchesTree(String n,ResourceDescriptor d){if(n.equals(d.resourceRef().resourceId()))return true;if(d.parentResource()!=null&&n.equals(d.parentResource().resourceId()))return true;return d.rootResource()!=null&&n.equals(d.rootResource().resourceId());}
 private boolean matchesTaskChain(String n,ResourceDescriptor d){if(d.resourceRef().resourceType()==ResourceType.TASK_CHAIN)return n.equals(d.resourceRef().resourceId());return d.rootResource()!=null&&n.equals(d.rootResource().resourceId());}
 private static void add(Set<String>s,String v){if(v!=null&&!v.isBlank())s.add(v);}
}
