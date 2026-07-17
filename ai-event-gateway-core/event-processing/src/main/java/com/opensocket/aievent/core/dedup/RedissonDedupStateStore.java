package com.opensocket.aievent.core.dedup;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.agitg.redisson.config.RedissonAccess;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;

/**
 * Shared-utility Redisson based dedup store.
 *
 * <p>P12.3 keeps the existing Spring Data Redis implementation available as store=REDIS,
 * but enterprise deployments should prefer store=REDISSON so Redis access goes through
 * shared-utility/redisson-client. The per-fingerprint Redisson lock keeps touch + count +
 * max severity update atomic across multiple core instances. Lock lease time is intentionally
 * short and independent from the dedup state TTL so an abnormal worker exit does not block
 * a hot fingerprint for the full dedup TTL.</p>
 */
@Component
@ConditionalOnProperty(prefix = "event.dedup", name = "store", havingValue = "REDISSON")
public class RedissonDedupStateStore implements DedupStateStore {
    private final RedissonAccess redissonAccess;
    private final EventDedupRedisProperties properties;

    public RedissonDedupStateStore(RedissonAccess redissonAccess, EventDedupRedisProperties properties) {
        this.redissonAccess = redissonAccess;
        this.properties = properties;
    }

    @Override
    public DedupDecision touch(String fingerprint, NormalizedEvent event, Duration duplicateWindow, Duration ttl) {
        String key = key(fingerprint);
        RLock lock = redissonAccess.getLock(lockKey(fingerprint));
        boolean locked = false;
        try {
            locked = lock.tryLock(properties.getLockWaitSeconds(), properties.getLockLeaseSeconds(), TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("Unable to acquire Redisson dedup lock for fingerprint=" + fingerprint);
            }

            RMap<String, String> map = redissonAccess.getMap(key);
            long occurredAtMillis = toEpochMillis(event.occurredAt());
            boolean exists = map.isExists();

            if (!exists || map.isEmpty()) {
                map.put("fingerprint", fingerprint);
                map.put("activeIncidentId", "");
                map.put("firstSeenAtMillis", String.valueOf(occurredAtMillis));
                map.put("lastSeenAtMillis", String.valueOf(occurredAtMillis));
                map.put("occurrenceCount", "1");
                map.put("maxSeverity", event.severity().name());
                map.put("maxSeverityRank", String.valueOf(severityRank(event.severity())));
                map.put("lastEventId", safe(event.eventId()));
                map.put("lastMessage", safe(event.normalizedMessage()));
                map.expire(ttl);
                DedupState state = readState(map, fingerprint);
                return new DedupDecision(false, state, "no active Redisson dedup state");
            }

            long lastSeenAtMillis = parseLong(map.get("lastSeenAtMillis"));
            boolean duplicate = lastSeenAtMillis > 0 && occurredAtMillis <= (lastSeenAtMillis + duplicateWindow.toMillis());
            long occurrenceCount = parseLong(map.get("occurrenceCount")) + 1;
            int currentSeverityRank = (int) parseLong(map.get("maxSeverityRank"));
            int incomingSeverityRank = severityRank(event.severity());
            EventSeverity maxSeverity = EventSeverity.parse(map.get("maxSeverity"));
            int maxSeverityRank = currentSeverityRank;
            if (incomingSeverityRank > currentSeverityRank) {
                maxSeverity = event.severity();
                maxSeverityRank = incomingSeverityRank;
            }
            long newLastSeenAtMillis = occurredAtMillis > lastSeenAtMillis ? occurredAtMillis : lastSeenAtMillis;

            map.put("lastSeenAtMillis", String.valueOf(newLastSeenAtMillis));
            map.put("occurrenceCount", String.valueOf(occurrenceCount));
            map.put("maxSeverity", maxSeverity.name());
            map.put("maxSeverityRank", String.valueOf(maxSeverityRank));
            map.put("lastEventId", safe(event.eventId()));
            map.put("lastMessage", safe(event.normalizedMessage()));
            map.expire(ttl);

            DedupState state = readState(map, fingerprint);
            return new DedupDecision(
                    duplicate,
                    state,
                    duplicate ? "same fingerprint within duplicate window" : "same fingerprint outside duplicate window"
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring Redisson dedup lock for fingerprint=" + fingerprint, ex);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void attachIncident(String fingerprint, String incidentId, Duration ttl) {
        String key = key(fingerprint);
        RLock lock = redissonAccess.getLock(lockKey(fingerprint));
        boolean locked = false;
        try {
            locked = lock.tryLock(properties.getLockWaitSeconds(), properties.getLockLeaseSeconds(), TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("Unable to acquire Redisson dedup lock for fingerprint=" + fingerprint);
            }
            RMap<String, String> map = redissonAccess.getMap(key);
            if (map.isExists() && !map.isEmpty()) {
                map.put("activeIncidentId", safe(incidentId));
                map.expire(ttl);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while attaching incident to Redisson dedup state fingerprint=" + fingerprint, ex);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Optional<DedupState> find(String fingerprint) {
        RMap<String, String> map = redissonAccess.getMap(key(fingerprint));
        if (!map.isExists() || map.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(readState(map, fingerprint));
    }

    @Override
    public String mode() {
        return "REDISSON";
    }

    private DedupState readState(RMap<String, String> map, String fingerprint) {
        return new DedupState(
                text(map.get("fingerprint"), fingerprint),
                blankToNull(map.get("activeIncidentId")),
                fromEpochMillis(map.get("firstSeenAtMillis")),
                fromEpochMillis(map.get("lastSeenAtMillis")),
                parseLong(map.get("occurrenceCount")),
                EventSeverity.parse(map.get("maxSeverity")),
                map.get("lastEventId"),
                map.get("lastMessage")
        );
    }

    private String key(String fingerprint) {
        return properties.getKeyPrefix() + ":event:dedup:" + fingerprint;
    }

    private String lockKey(String fingerprint) {
        return properties.getKeyPrefix() + ":event:dedup-lock:" + fingerprint;
    }

    private int severityRank(EventSeverity severity) {
        return severity == null ? EventSeverity.MEDIUM.ordinal() : severity.ordinal();
    }

    private long toEpochMillis(OffsetDateTime value) {
        OffsetDateTime effective = value == null ? OffsetDateTime.now(ZoneOffset.UTC) : value;
        return effective.toInstant().toEpochMilli();
    }

    private OffsetDateTime fromEpochMillis(String value) {
        long millis = parseLong(value);
        if (millis <= 0) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC);
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            return 0L;
        }
    }

    private String text(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
