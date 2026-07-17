package com.opensocket.aievent.core.decision;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EventDecisionQueryService {
    private final EventDecisionRepository repository;
    public EventDecisionQueryService(EventDecisionRepository repository){this.repository=repository;}
    public List<EventDecisionRecord> recent(int limit){return repository.findRecent(Math.max(1,limit));}
    public String storeMode(){return repository.getClass().getSimpleName();}
}
