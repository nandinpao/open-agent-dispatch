package com.opensocket.aievent.core.integration.issue;
/** Provider execution boundary for asynchronous Issue Projection operations. */
public interface IssueSyncProviderGateway {
 boolean supports(IntegrationOutboxEntry entry);
 ProviderSyncResult execute(IntegrationOutboxEntry entry);
 default int priority(){ return 100; }
 default String mode(){ return getClass().getSimpleName(); }
}
