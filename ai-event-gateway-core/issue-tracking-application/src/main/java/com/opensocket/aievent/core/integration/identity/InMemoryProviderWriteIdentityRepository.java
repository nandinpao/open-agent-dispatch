package com.opensocket.aievent.core.integration.identity;
import com.opensocket.aievent.core.issuetracking.identity.*;
import java.util.*; import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Repository;
@Repository @Profile("!prod") @ConditionalOnProperty(prefix="integration-identity",name="store",havingValue="MEMORY")
public final class InMemoryProviderWriteIdentityRepository implements ExternalActorBindingRepository,ProviderExecutionAttributionRepository {
 private final Map<String,ExternalActorBindingRef> bindings=new ConcurrentHashMap<>(); private final Map<String,ProviderExecutionAttribution> attributions=new ConcurrentHashMap<>();
 private static String k(String t,String id){return t+":"+id;}
 @Override public Optional<ExternalActorBindingRef> findVerified(String t,String h,String c){return bindings.values().stream().filter(v->t.equals(v.tenantId())&&h.equals(v.humanPrincipalId())&&c.equals(v.connectionId())&&v.verifiedAt(java.time.OffsetDateTime.now())).findFirst();}
 @Override public ExternalActorBindingRef save(ExternalActorBindingRef v){bindings.put(k(v.tenantId(),v.bindingId()),v);return v;}
 @Override public ProviderExecutionAttribution append(ProviderExecutionAttribution v){var old=attributions.putIfAbsent(k(v.tenantId(),v.idempotencyKey()),v);return old==null?v:old;}
 @Override public Optional<ProviderExecutionAttribution> findByIdempotencyKey(String t,String id){return Optional.ofNullable(attributions.get(k(t,id)));}
}
