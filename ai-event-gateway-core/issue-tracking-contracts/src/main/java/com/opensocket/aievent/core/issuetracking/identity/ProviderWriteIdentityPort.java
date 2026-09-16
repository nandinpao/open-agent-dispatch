package com.opensocket.aievent.core.issuetracking.identity;
import com.opensocket.aievent.core.integration.identity.IntegrationOperation; import java.time.OffsetDateTime;
public interface ProviderWriteIdentityPort {
 ProviderWriteIdentityResolution resolve(String tenantId,String humanPrincipalId,String mappingId,IntegrationOperation operation,OffsetDateTime at);
}
