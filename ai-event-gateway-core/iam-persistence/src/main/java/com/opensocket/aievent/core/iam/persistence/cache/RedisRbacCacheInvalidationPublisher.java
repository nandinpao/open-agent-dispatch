package com.opensocket.aievent.core.iam.persistence.cache;

import com.opensocket.aievent.core.iam.rbac.application.port.out.CacheInvalidationPort;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Redis Pub/Sub is a post-commit fast path; the transactional outbox remains the durable authority. */
public final class RedisRbacCacheInvalidationPublisher implements CacheInvalidationPort {
    public static final String DEFAULT_CHANNEL = "opendispatch:iam:cache-invalidation";
    private final StringRedisTemplate redis;
    private final String channel;

    public RedisRbacCacheInvalidationPublisher(StringRedisTemplate redis, String channel) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.channel = channel == null || channel.isBlank() ? DEFAULT_CHANNEL : channel.trim();
    }

    @Override
    public void publish(CacheInvalidation invalidation) {
        Objects.requireNonNull(invalidation, "invalidation");
        Runnable send = () -> redis.convertAndSend(channel, serialize(invalidation));
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }

    private String serialize(CacheInvalidation invalidation) {
        String principals = String.join(",", invalidation.principalIds());
        return String.join("|", safe(invalidation.eventId()), safe(invalidation.tenantId()),
                Long.toString(invalidation.policyVersion()), Long.toString(invalidation.securityEpoch()),
                Long.toString(invalidation.occurredAt().toEpochMilli()), safe(principals));
    }

    private String safe(String value) { return value == null ? "" : value.replace("|", ""); }
}
