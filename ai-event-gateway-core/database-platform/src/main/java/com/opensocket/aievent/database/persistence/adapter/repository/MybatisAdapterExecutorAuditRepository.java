package com.opensocket.aievent.database.persistence.adapter.repository;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRecord;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRepository;
import com.opensocket.aievent.database.persistence.adapter.dao.AdapterExecutorAuditDao;
import com.opensocket.aievent.database.persistence.adapter.po.AdapterExecutorAuditPo;
import com.opensocket.aievent.database.persistence.adapter.converter.AdapterExecutorAuditPersistenceConverter;


@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="adapter-executor.audit", name="store", havingValue="MYBATIS")
public class MybatisAdapterExecutorAuditRepository implements AdapterExecutorAuditRepository {
    private final AdapterExecutorAuditDao dao;
    private final AdapterExecutorAuditPersistenceConverter converter;

    public MybatisAdapterExecutorAuditRepository(AdapterExecutorAuditDao dao, AdapterExecutorAuditPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    public AdapterExecutorAuditRecord save(AdapterExecutorAuditRecord record){dao.insertIgnore(converter.toPo(record));return record;}

    public List<AdapterExecutorAuditRecord> recent(int limit){return dao.recent(cap(limit)).stream().map(converter::toDomain).toList();}

    public List<AdapterExecutorAuditRecord> findByActionId(String actionId,int limit){return dao.findByActionId(actionId,cap(limit)).stream().map(converter::toDomain).toList();}

    public String mode(){return "MYBATIS";}

    private int cap(int n){return Math.max(1,Math.min(n,1000));}
}
