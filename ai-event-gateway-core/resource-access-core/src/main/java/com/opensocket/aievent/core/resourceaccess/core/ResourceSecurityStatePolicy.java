package com.opensocket.aievent.core.resourceaccess.core;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Optional;
/** Resource-state restrictions evaluated before any allow source. */
public final class ResourceSecurityStatePolicy {
 public Optional<String> blockingReason(ResourceSecurityState state,ResourceAction action,java.util.Map<String,String> trustedFlow){
  boolean investigator="true".equalsIgnoreCase(trustedFlow.getOrDefault("security-investigator","false"));
  return switch(state){
   case NORMAL,RESTRICTED->Optional.empty();
   case QUARANTINED->investigator?Optional.empty():Optional.of(ResourceDecisionReasonCodes.RESOURCE_QUARANTINED);
   case INVESTIGATION->investigator?Optional.empty():Optional.of(ResourceDecisionReasonCodes.RESOURCE_INVESTIGATION_RESTRICTED);
   case LEGAL_HOLD->(action.kind()==ResourceAction.ActionKind.DELETE)?Optional.of(ResourceDecisionReasonCodes.RESOURCE_LEGAL_HOLD_RESTRICTED):Optional.empty();
   case ARCHIVED->action.sideEffecting()?Optional.of(ResourceDecisionReasonCodes.RESOURCE_ARCHIVED_READ_ONLY):Optional.empty();
   case DELETED->Optional.of(ResourceDecisionReasonCodes.RESOURCE_DELETED);
   case ORPHANED->Optional.of(ResourceDecisionReasonCodes.RESOURCE_ORPHANED);
  };
 }
 public boolean highRisk(ResourceDescriptor d,ResourceAction a){return d.securityState()!=ResourceSecurityState.NORMAL||d.visibility().sensitivityLevel()==SensitivityLevel.SECRET||a.kind()==ResourceAction.ActionKind.EXPORT||a.kind()==ResourceAction.ActionKind.DOWNLOAD||a.sideEffecting();}
}
