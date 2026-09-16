package com.opensocket.aievent.core.integration.issue;
public interface IssueSyncReadbackGateway {
 boolean supportsReadback(IntegrationOutboxEntry entry);
 ProjectionProviderReadbackResult readback(IntegrationOutboxEntry entry,String externalIdempotencyMarker,String providerRequestFingerprint);
 default int priority(){return 100;}
 default String mode(){return "CUSTOM";}
}
