package com.opensocket.aievent.database.persistence.issuepolicy;

import java.util.List;
import java.util.Optional;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.integration.issue.policy.*;
import com.opensocket.aievent.database.persistence.issuepolicy.dao.IssuePolicyDecisionDao;

@DatabaseRepositoryAdapter
public class MybatisIssuePolicyDecisionRepository implements IssuePolicyDecisionRepository {
    private final IssuePolicyDecisionDao dao; private final IssuePolicyDecisionPersistenceConverter converter;
    public MybatisIssuePolicyDecisionRepository(IssuePolicyDecisionDao dao,IssuePolicyDecisionPersistenceConverter converter){this.dao=dao;this.converter=converter;}
    @Override public IssuePolicyDecision save(IssuePolicyDecision value){var current=dao.find(value.tenantId(),value.decisionId());if(current==null){dao.insert(converter.toPo(value));}else if(dao.updateCas(converter.toPo(value),current.getVersion())!=1){throw new IllegalStateException("RESOURCE_VERSION_CONFLICT: issue policy decision");}return find(value.tenantId(),value.decisionId()).orElse(value);}
    @Override public Optional<IssuePolicyDecision> find(String tenantId,String decisionId){return Optional.ofNullable(dao.find(tenantId,decisionId)).map(converter::toDomain);}
    @Override public Optional<IssuePolicyDecision> findByTaskAndPurpose(String tenantId,String taskId,String projectionPurpose){return Optional.ofNullable(dao.findByTaskAndPurpose(tenantId,taskId,projectionPurpose)).map(converter::toDomain);}
    @Override public List<IssuePolicyDecision> listByTask(String tenantId,String taskId,int limit){return dao.listByTask(tenantId,taskId,Math.max(1,Math.min(limit<=0?100:limit,1000))).stream().map(converter::toDomain).toList();}
}
