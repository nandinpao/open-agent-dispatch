package com.opensocket.aievent.database.persistence.eventprocessing.repository;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.decision.DecisionAction;
import com.opensocket.aievent.core.decision.DecisionType;
import com.opensocket.aievent.core.decision.EventDecisionRecord;
import com.opensocket.aievent.core.decision.EventDecisionRepository;
import com.opensocket.aievent.database.persistence.eventprocessing.dao.EventDecisionDao;
import com.opensocket.aievent.database.persistence.eventprocessing.po.EventDecisionPo;
import com.opensocket.aievent.database.persistence.eventprocessing.converter.EventDecisionPersistenceConverter;


@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="event.decisions", name="store", havingValue="MYBATIS")
public class MybatisEventDecisionRepository implements EventDecisionRepository {
    private final EventDecisionDao dao;
    private final EventDecisionPersistenceConverter converter;

    public MybatisEventDecisionRepository(EventDecisionDao dao, EventDecisionPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    public EventDecisionRecord save(EventDecisionRecord record){dao.upsert(converter.toPo(record));return record;}

    public List<EventDecisionRecord> findRecent(int limit){return dao.findRecent(cap(limit)).stream().map(converter::toDomain).toList();}

    private int cap(int n){return Math.max(1,Math.min(n,1000));}
}
