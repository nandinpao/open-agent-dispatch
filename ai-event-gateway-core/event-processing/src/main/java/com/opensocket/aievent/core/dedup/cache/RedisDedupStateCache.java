package com.opensocket.aievent.core.dedup.cache;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.dedup.EventDedupRedisProperties;
import com.opensocket.aievent.core.event.NormalizedEvent;

@Component
@ConditionalOnProperty(prefix = "event.dedup", name = "cache-store", havingValue = "REDIS")
public class RedisDedupStateCache implements DedupStateCache {
    private final StringRedisTemplate redis;
    private final EventDedupRedisProperties properties;

    public RedisDedupStateCache(StringRedisTemplate redis, EventDedupRedisProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void publish(DedupState state, NormalizedEvent event, Duration ttl) {
        if (state == null || state.getFingerprint() == null) {
            return;
        }
        String key = key(state.getFingerprint());
        redis.opsForHash().put(key, "fingerprint", safe(state.getFingerprint()));
        redis.opsForHash().put(key, "activeIncidentId", safe(state.getActiveIncidentId()));
        redis.opsForHash().put(key, "firstSeenAtMillis", millis(state.getFirstSeenAt()));
        redis.opsForHash().put(key, "lastSeenAtMillis", millis(state.getLastSeenAt()));
        redis.opsForHash().put(key, "occurrenceCount", String.valueOf(state.getOccurrenceCount()));
        redis.opsForHash().put(key, "maxSeverity", state.getMaxSeverity() == null ? "MEDIUM" : state.getMaxSeverity().name());
        redis.opsForHash().put(key, "maxSeverityRank", String.valueOf(state.getMaxSeverity() == null ? 1 : state.getMaxSeverity().ordinal()));
        redis.opsForHash().put(key, "lastEventId", safe(state.getLastEventId()));
        redis.opsForHash().put(key, "lastMessage", safe(state.getLastMessage()));
        redis.expire(key, ttl);
    }

    @Override
    public String mode() {
        return "REDIS";
    }

    private String key(String fingerprint) {
        return properties.getKeyPrefix() + ":event:dedup:" + fingerprint;
    }

    private String millis(java.time.OffsetDateTime value) {
        return String.valueOf(value == null ? 0L : value.toInstant().toEpochMilli());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
