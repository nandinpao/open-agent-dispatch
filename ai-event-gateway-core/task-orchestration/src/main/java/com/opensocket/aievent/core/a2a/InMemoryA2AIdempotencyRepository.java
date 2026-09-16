package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix="task", name="store", havingValue="MEMORY")
public class InMemoryA2AIdempotencyRepository implements A2AIdempotencyRepository {
    private final ConcurrentHashMap<String, A2AIdempotencyRecord> values = new ConcurrentHashMap<>();

    @Override
    public A2AIdempotencyClaim claim(String tenantId, String idempotencyKey, String operationType,
                                      String requestHash, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        String key = key(tenantId,idempotencyKey,operationType);
        A2AIdempotencyRecord created = new A2AIdempotencyRecord();
        created.setTenantId(tenantId);
        created.setIdempotencyKey(idempotencyKey);
        created.setOperationType(operationType);
        created.setRequestHash(requestHash);
        created.setCreatedAt(createdAt);
        created.setExpiresAt(expiresAt);
        A2AIdempotencyRecord existing = values.putIfAbsent(key, created);
        return new A2AIdempotencyClaim(existing == null ? created : existing, existing == null);
    }

    @Override
    public Optional<A2AIdempotencyRecord> find(String tenantId, String idempotencyKey, String operationType) {
        return Optional.ofNullable(values.get(key(tenantId,idempotencyKey,operationType)));
    }

    @Override
    public void complete(String tenantId, String idempotencyKey, String operationType,
                         String resourceType, String resourceId, OffsetDateTime completedAt) {
        values.computeIfPresent(key(tenantId,idempotencyKey,operationType), (ignored,value) -> {
            value.setResourceType(resourceType);
            value.setResourceId(resourceId);
            value.setResultStatus(A2AIdempotencyStatus.COMPLETED);
            value.setCompletedAt(completedAt);
            return value;
        });
    }

    @Override public String mode(){ return "MEMORY"; }
    private String key(String tenantId,String idempotencyKey,String operationType){return tenantId+":"+operationType+":"+idempotencyKey;}
}
