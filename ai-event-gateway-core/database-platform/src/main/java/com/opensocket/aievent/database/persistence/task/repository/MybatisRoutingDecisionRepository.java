package com.opensocket.aievent.database.persistence.task.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.database.persistence.task.dao.RoutingDecisionDao;
import com.opensocket.aievent.database.persistence.task.po.RoutingDecisionPo;
import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionRepository;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.routing.RoutingPolicy;
import com.opensocket.aievent.database.persistence.task.converter.RoutingDecisionPersistenceConverter;


@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="routing", name="decision-store", havingValue="MYBATIS")
public class MybatisRoutingDecisionRepository implements RoutingDecisionRepository {
    private final RoutingDecisionDao dao;
    private final RoutingDecisionPersistenceConverter converter;

    public MybatisRoutingDecisionRepository(RoutingDecisionDao dao, RoutingDecisionPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    public RoutingDecisionRecord save(RoutingDecisionRecord d){dao.upsert(converter.toPo(d));return d;}

    public Optional<RoutingDecisionRecord> findById(String id){return Optional.ofNullable(dao.findById(id)).map(converter::toDomain);}

    public List<RoutingDecisionRecord> findByTaskId(String taskId,int limit){return dao.findByTaskId(taskId,cap(limit)).stream().map(converter::toDomain).toList();}

    public List<RoutingDecisionRecord> recent(int limit){return dao.recent(cap(limit)).stream().map(converter::toDomain).toList();}

    public String mode(){return "MYBATIS";}

    private int cap(int n){return Math.max(1,Math.min(n,1000));}
}
