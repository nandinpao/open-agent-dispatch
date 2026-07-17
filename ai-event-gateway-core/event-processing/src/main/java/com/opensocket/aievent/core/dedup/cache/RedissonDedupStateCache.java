package com.opensocket.aievent.core.dedup.cache;

import java.time.Duration;

import org.redisson.api.RMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.agitg.redisson.config.RedissonAccess;
import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.dedup.EventDedupRedisProperties;
import com.opensocket.aievent.core.event.NormalizedEvent;

@Component
@ConditionalOnProperty(prefix = "event.dedup", name = "cache-store", havingValue = "REDISSON")
public class RedissonDedupStateCache implements DedupStateCache {
    private final RedissonAccess redissonAccess;
    private final EventDedupRedisProperties properties;

    public RedissonDedupStateCache(RedissonAccess redissonAccess, EventDedupRedisProperties properties) {
        this.redissonAccess = redissonAccess;
        this.properties = properties;
    }

    @Override
    public void publish(DedupState state, NormalizedEvent event, Duration ttl) {
        if (state == null || state.getFingerprint() == null) {
            return;
        }
        RMap<String, String> map = redissonAccess.getMap(key(state.getFingerprint()));
        map.put("fingerprint", safe(state.getFingerprint()));
        map.put("activeIncidentId", safe(state.getActiveIncidentId()));
        map.put("firstSeenAtMillis", millis(state.getFirstSeenAt()));
        map.put("lastSeenAtMillis", millis(state.getLastSeenAt()));
        map.put("occurrenceCount", String.valueOf(state.getOccurrenceCount()));
        map.put("maxSeverity", state.getMaxSeverity() == null ? "MEDIUM" : state.getMaxSeverity().name());
        map.put("maxSeverityRank", String.valueOf(state.getMaxSeverity() == null ? 1 : state.getMaxSeverity().ordinal()));
        map.put("lastEventId", safe(state.getLastEventId()));
        map.put("lastMessage", safe(state.getLastMessage()));
        map.expire(ttl);
    }

    @Override
    public String mode() {
        return "REDISSON";
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
