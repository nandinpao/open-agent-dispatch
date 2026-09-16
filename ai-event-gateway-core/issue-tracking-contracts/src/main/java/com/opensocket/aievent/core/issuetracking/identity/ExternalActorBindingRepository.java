package com.opensocket.aievent.core.issuetracking.identity;
import java.util.Optional;
public interface ExternalActorBindingRepository {
 Optional<ExternalActorBindingRef> findVerified(String tenantId,String humanPrincipalId,String connectionId);
 ExternalActorBindingRef save(ExternalActorBindingRef value);
}
