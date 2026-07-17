package com.opensocket.aievent.core.outbox;

import java.util.List;
import java.util.Map;

/** Read-only operational view for outbox health and support tooling. */
public interface OutboxOperationalQuery {
    List<OutboxEventRecord> recent(int limit);

    Map<String, Integer> statusCounts(int sampleLimit);

    String storeMode();
}
