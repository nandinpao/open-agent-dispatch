package com.opensocket.aievent.core.integration.identity;
import java.util.List;
public record ProviderIssueTypeMetadata(String issueTypeId,String issueTypeKey,String displayName,List<String> requiredFieldIds) {
 public ProviderIssueTypeMetadata { requiredFieldIds=requiredFieldIds==null?List.of():List.copyOf(requiredFieldIds); }
}
