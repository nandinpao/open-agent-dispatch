package com.opensocket.aievent.core.outbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class DefaultOutboxOperationalQuery implements OutboxOperationalQuery {
    private final OutboxEventRepository repository;

    public DefaultOutboxOperationalQuery(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<OutboxEventRecord> recent(int limit) {
        return repository.recent(Math.max(1, limit));
    }

    @Override
    public Map<String, Integer> statusCounts(int sampleLimit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OutboxEventStatus status : OutboxEventStatus.values()) {
            counts.put(status.name(), 0);
        }
        for (OutboxEventRecord event : repository.recent(Math.max(1, sampleLimit))) {
            if (event.getStatus() != null) {
                counts.compute(event.getStatus().name(), (key, value) -> value == null ? 1 : value + 1);
            }
        }
        return counts;
    }

    @Override
    public String storeMode() {
        return repository.mode();
    }
}
