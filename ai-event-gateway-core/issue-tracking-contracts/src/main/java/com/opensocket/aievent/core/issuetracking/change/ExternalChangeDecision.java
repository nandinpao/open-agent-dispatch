package com.opensocket.aievent.core.issuetracking.change;
import java.util.Objects; import com.opensocket.aievent.core.integration.identity.IntegrationRiskLevel;
public record ExternalChangeDecision(String policyId,ExternalChangeDecisionAction action,IntegrationRiskLevel riskLevel,boolean requiresReauthentication,boolean requiresApproval,boolean allowProviderMutation,String reasonCode) {
 public ExternalChangeDecision { policyId=policyId==null?"":policyId.trim();action=Objects.requireNonNull(action,"action is required");riskLevel=riskLevel==null?IntegrationRiskLevel.UNKNOWN:riskLevel;reasonCode=req(reasonCode,"reasonCode"); }
 private static String req(String v,String n){Objects.requireNonNull(v,n+" is required");String x=v.trim();if(x.isEmpty())throw new IllegalArgumentException(n+" is required");return x;}
}
