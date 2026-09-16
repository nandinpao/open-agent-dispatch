package com.opensocket.aievent.core.integration.issue;
import java.util.Map;
public record IssueProjectionCommand(String connectionId,String projectMappingId,String providerType,
 String externalProjectId,String externalProjectKey,String externalIssueType,TaskIssueLinkRole linkRole,
 IssueProjectionStrategy strategy,Map<String,Object> payload) {
 public IssueProjectionCommand { linkRole=linkRole==null?TaskIssueLinkRole.PRIMARY:linkRole; strategy=strategy==null?IssueProjectionStrategy.CREATE_NEW_ISSUE:strategy; payload=payload==null?Map.of():Map.copyOf(payload); }
}
