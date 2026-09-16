package com.opensocket.aievent.core.issuetracking.relay;
import java.util.Objects;
public record RelayEdgeDefinition(String connectionId,String projectMappingId,String providerType,String externalProjectId,String sourceExternalIssueId,String targetExternalIssueId,RelayEdgeType edgeType,String relationType) {
 public RelayEdgeDefinition { connectionId=req(connectionId,"connectionId");projectMappingId=norm(projectMappingId);providerType=req(providerType,"providerType");externalProjectId=norm(externalProjectId);sourceExternalIssueId=req(sourceExternalIssueId,"sourceExternalIssueId");targetExternalIssueId=norm(targetExternalIssueId);edgeType=Objects.requireNonNull(edgeType,"edgeType is required");relationType=req(relationType,"relationType"); }
 private static String req(String v,String n){Objects.requireNonNull(v,n+" is required");String x=v.trim();if(x.isEmpty())throw new IllegalArgumentException(n+" is required");return x;} private static String norm(String v){return v==null?"":v.trim();}
}
