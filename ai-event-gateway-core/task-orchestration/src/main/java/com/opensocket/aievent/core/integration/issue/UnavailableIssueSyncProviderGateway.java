package com.opensocket.aievent.core.integration.issue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
/** Fail-closed fallback when no Jira/Redmine projection adapter is installed. */
@Component
@ConditionalOnMissingBean(IssueSyncProviderGateway.class)
public class UnavailableIssueSyncProviderGateway implements IssueSyncProviderGateway {
 public boolean supports(IntegrationOutboxEntry entry){ return true; }
 public ProviderSyncResult execute(IntegrationOutboxEntry entry){
  return ProviderSyncResult.failure(false,503,"ISSUE_PROVIDER_NOT_CONFIGURED","No scoped Issue Provider gateway is configured for this operation.");
 }
 public int priority(){ return Integer.MAX_VALUE; }
 public String mode(){ return "UNAVAILABLE_FAIL_CLOSED"; }
}
