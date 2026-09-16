package com.opensocket.aievent.core.issuetracking.core;
import java.time.OffsetDateTime; import java.util.*;
import com.opensocket.aievent.core.integration.identity.IntegrationRiskLevel;
import com.opensocket.aievent.core.issuetracking.change.*;
/** Pure provider-neutral policy selection. More specific scopes and higher priority win. */
public final class ExternalChangePolicyEngine {
 public ExternalChangeDecision evaluate(ExternalChangeEvaluationContext context,List<ExternalChangePolicy> policies,OffsetDateTime now){
  Objects.requireNonNull(context,"context is required");Objects.requireNonNull(now,"now is required");
  return (policies==null?List.<ExternalChangePolicy>of():policies).stream().filter(p->p.activeAt(now)).filter(p->matches(p,context))
   .sorted(Comparator.<ExternalChangePolicy>comparingInt(p->specificity(p,context)).reversed().thenComparing(Comparator.comparingInt(ExternalChangePolicy::priority).reversed()).thenComparing(ExternalChangePolicy::policyId))
   .findFirst().map(this::decision).orElseGet(()->defaultDecision(context));
 }
 private boolean matches(ExternalChangePolicy p,ExternalChangeEvaluationContext c){
  if(!p.tenantId().equals(c.tenantId()))return false;if(p.resourceType()!=c.resourceType())return false;
  if(p.direction()!=ExternalChangeDirection.BIDIRECTIONAL&&p.direction()!=c.direction())return false;
  return eqOrEmpty(p.connectionId(),c.connectionId())&&eqOrEmpty(p.projectMappingId(),c.projectMappingId())&&eqOrEmpty(p.externalProjectId(),c.externalProjectId())&&eqOrEmpty(p.issueType(),c.issueType())&&fieldMatches(p.fieldPath(),c.fieldPath());
 }
 private int specificity(ExternalChangePolicy p,ExternalChangeEvaluationContext c){int s=0;if(!p.connectionId().isBlank())s+=16;if(!p.projectMappingId().isBlank())s+=8;if(!p.externalProjectId().isBlank())s+=4;if(!p.issueType().isBlank())s+=2;if(!p.fieldPath().isBlank())s+=1;return s;}
 private ExternalChangeDecision decision(ExternalChangePolicy p){return new ExternalChangeDecision(p.policyId(),p.action(),p.riskLevel(),p.requiresReauthentication(),p.requiresApproval(),p.allowProviderMutation(),"POLICY_MATCHED");}
 private ExternalChangeDecision defaultDecision(ExternalChangeEvaluationContext c){return switch(c.resourceType()){
  case COMMENT,LINK -> new ExternalChangeDecision("",ExternalChangeDecisionAction.ACCEPT,IntegrationRiskLevel.LOW,false,false,true,"DEFAULT_COLLABORATION_ACCEPT");
  case STATUS,APPROVAL,PRIORITY,ASSIGNEE,ISSUE_FIELD -> new ExternalChangeDecision("",ExternalChangeDecisionAction.CREATE_CANDIDATE,IntegrationRiskLevel.HIGH,true,true,false,"DEFAULT_AUTHORITY_CANDIDATE");
  case ISSUE_DELETE,PROJECT_MOVE -> new ExternalChangeDecision("",ExternalChangeDecisionAction.CREATE_CONFLICT,IntegrationRiskLevel.CRITICAL,true,true,false,"DEFAULT_DESTRUCTIVE_CONFLICT");
  default -> new ExternalChangeDecision("",ExternalChangeDecisionAction.CREATE_CONFLICT,IntegrationRiskLevel.MEDIUM,false,false,false,"DEFAULT_UNCLASSIFIED_CONFLICT");};}
 private boolean eqOrEmpty(String expected,String actual){return expected==null||expected.isBlank()||Objects.equals(expected,actual);} private boolean fieldMatches(String expected,String actual){if(expected==null||expected.isBlank())return true;if(expected.endsWith("/*")){String prefix=expected.substring(0,expected.length()-1);return actual!=null&&actual.startsWith(prefix);}return Objects.equals(expected,actual);}
}
