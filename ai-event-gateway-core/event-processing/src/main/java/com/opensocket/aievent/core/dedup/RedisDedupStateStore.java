package com.opensocket.aievent.core.dedup;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;

@Component
@ConditionalOnProperty(prefix = "event.dedup", name = "store", havingValue = "REDIS")
public class RedisDedupStateStore implements DedupStateStore {
    private static final DefaultRedisScript<List> TOUCH_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local fingerprint = ARGV[1]
            local occurredAtMillis = tonumber(ARGV[2])
            local duplicateWindowMillis = tonumber(ARGV[3])
            local ttlSeconds = tonumber(ARGV[4])
            local severityName = ARGV[5]
            local severityRank = tonumber(ARGV[6])
            local lastEventId = ARGV[7]
            local lastMessage = ARGV[8]

            if redis.call('EXISTS', key) == 0 then
              redis.call('HSET', key,
                'fingerprint', fingerprint,
                'activeIncidentId', '',
                'firstSeenAtMillis', tostring(occurredAtMillis),
                'lastSeenAtMillis', tostring(occurredAtMillis),
                'occurrenceCount', '1',
                'maxSeverity', severityName,
                'maxSeverityRank', tostring(severityRank),
                'lastEventId', lastEventId,
                'lastMessage', lastMessage)
              redis.call('EXPIRE', key, ttlSeconds)
              return {'0', fingerprint, '', tostring(occurredAtMillis), tostring(occurredAtMillis), '1', severityName, tostring(severityRank), lastEventId, lastMessage, 'no active Redis dedup state'}
            end

            local lastSeenAtMillis = tonumber(redis.call('HGET', key, 'lastSeenAtMillis') or '0')
            local duplicate = '0'
            if lastSeenAtMillis > 0 and occurredAtMillis <= (lastSeenAtMillis + duplicateWindowMillis) then
              duplicate = '1'
            end
            local occurrenceCount = tonumber(redis.call('HGET', key, 'occurrenceCount') or '0') + 1
            local currentSeverityRank = tonumber(redis.call('HGET', key, 'maxSeverityRank') or '0')
            local maxSeverity = redis.call('HGET', key, 'maxSeverity') or 'MEDIUM'
            local maxSeverityRank = currentSeverityRank
            if severityRank > currentSeverityRank then
              maxSeverity = severityName
              maxSeverityRank = severityRank
            end
            local firstSeenAtMillis = redis.call('HGET', key, 'firstSeenAtMillis') or tostring(occurredAtMillis)
            local newLastSeenAtMillis = lastSeenAtMillis
            if occurredAtMillis > lastSeenAtMillis then
              newLastSeenAtMillis = occurredAtMillis
            end
            local activeIncidentId = redis.call('HGET', key, 'activeIncidentId') or ''
            redis.call('HSET', key,
              'lastSeenAtMillis', tostring(newLastSeenAtMillis),
              'occurrenceCount', tostring(occurrenceCount),
              'maxSeverity', maxSeverity,
              'maxSeverityRank', tostring(maxSeverityRank),
              'lastEventId', lastEventId,
              'lastMessage', lastMessage)
            redis.call('EXPIRE', key, ttlSeconds)
            local reason = 'same fingerprint outside duplicate window'
            if duplicate == '1' then
              reason = 'same fingerprint within duplicate window'
            end
            return {duplicate, fingerprint, activeIncidentId, tostring(firstSeenAtMillis), tostring(newLastSeenAtMillis), tostring(occurrenceCount), maxSeverity, tostring(maxSeverityRank), lastEventId, lastMessage, reason}
            """, List.class);

    private final StringRedisTemplate redis;
    private final EventDedupRedisProperties properties;

    public RedisDedupStateStore(StringRedisTemplate redis, EventDedupRedisProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public DedupDecision touch(String fingerprint, NormalizedEvent event, Duration duplicateWindow, Duration ttl) {
        long occurredAtMillis = toEpochMillis(event.occurredAt());
        List<?> result = redis.execute(
                TOUCH_SCRIPT,
                List.of(key(fingerprint)),
                fingerprint,
                String.valueOf(occurredAtMillis),
                String.valueOf(duplicateWindow.toMillis()),
                String.valueOf(Math.max(1, ttl.toSeconds())),
                event.severity().name(),
                String.valueOf(severityRank(event.severity())),
                safe(event.eventId()),
                safe(event.normalizedMessage())
        );
        if (result == null || result.size() < 11) {
            throw new IllegalStateException("Redis dedup touch script returned an invalid result for fingerprint=" + fingerprint);
        }
        boolean duplicate = "1".equals(value(result, 0));
        DedupState state = stateFrom(result);
        return new DedupDecision(duplicate, state, value(result, 10));
    }

    @Override
    public void attachIncident(String fingerprint, String incidentId, Duration ttl) {
        String key = key(fingerprint);
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            redis.opsForHash().put(key, "activeIncidentId", safe(incidentId));
            redis.expire(key, ttl);
        }
    }

    @Override
    public Optional<DedupState> find(String fingerprint) {
        Map<Object, Object> values = redis.opsForHash().entries(key(fingerprint));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DedupState(
                text(values.get("fingerprint"), fingerprint),
                blankToNull(text(values.get("activeIncidentId"), null)),
                fromEpochMillis(text(values.get("firstSeenAtMillis"), "0")),
                fromEpochMillis(text(values.get("lastSeenAtMillis"), "0")),
                parseLong(text(values.get("occurrenceCount"), "0")),
                EventSeverity.parse(text(values.get("maxSeverity"), "MEDIUM")),
                text(values.get("lastEventId"), null),
                text(values.get("lastMessage"), null)
        ));
    }

    @Override
    public String mode() {
        return "REDIS";
    }

    private DedupState stateFrom(List<?> values) {
        return new DedupState(
                value(values, 1),
                blankToNull(value(values, 2)),
                fromEpochMillis(value(values, 3)),
                fromEpochMillis(value(values, 4)),
                parseLong(value(values, 5)),
                EventSeverity.parse(value(values, 6)),
                value(values, 8),
                value(values, 9)
        );
    }

    private String key(String fingerprint) {
        return properties.getKeyPrefix() + ":event:dedup:" + fingerprint;
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

    private String value(List<?> values, int index) {
        Object value = values.get(index);
        return value == null ? "" : String.valueOf(value);
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
