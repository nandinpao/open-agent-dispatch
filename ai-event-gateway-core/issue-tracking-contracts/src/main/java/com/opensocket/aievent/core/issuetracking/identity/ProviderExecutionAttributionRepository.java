package com.opensocket.aievent.core.issuetracking.identity;
import java.util.Optional;
public interface ProviderExecutionAttributionRepository {
 ProviderExecutionAttribution append(ProviderExecutionAttribution value);
 Optional<ProviderExecutionAttribution> findByIdempotencyKey(String tenantId,String idempotencyKey);
}
