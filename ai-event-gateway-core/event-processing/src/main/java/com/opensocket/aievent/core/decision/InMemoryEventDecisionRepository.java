package com.opensocket.aievent.core.decision;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "event.decisions", name = "store", havingValue = "MEMORY")
public class InMemoryEventDecisionRepository implements EventDecisionRepository {
    private final ArrayDeque<EventDecisionRecord> records = new ArrayDeque<>();
    private final int maxRecords = 10000;

    @Override
    public synchronized EventDecisionRecord save(EventDecisionRecord record) {
        records.addFirst(record);
        while (records.size() > maxRecords) {
            records.removeLast();
        }
        return record;
    }

    @Override
    public synchronized List<EventDecisionRecord> findRecent(int limit) {
        return records.stream()
                .limit(Math.max(1, Math.min(limit, maxRecords)))
                .sorted(Comparator.comparing(EventDecisionRecord::getDecidedAt).reversed())
                .toList();
    }
}
