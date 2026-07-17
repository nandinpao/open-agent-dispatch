package com.opensocket.aievent.core.decision;

import java.util.List;

public interface EventDecisionRepository {
    EventDecisionRecord save(EventDecisionRecord record);
    List<EventDecisionRecord> findRecent(int limit);
}
