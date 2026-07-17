package com.opensocket.aievent.database.persistence.agent.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.gateway.GatewayNode;
import com.opensocket.aievent.core.gateway.GatewayNodeQuery;
import com.opensocket.aievent.core.gateway.GatewayNodeRepository;
import com.opensocket.aievent.core.gateway.GatewayNodeStatus;
import com.opensocket.aievent.database.persistence.agent.dao.GatewayNodeDao;
import com.opensocket.aievent.database.persistence.agent.po.GatewayNodePo;
import com.opensocket.aievent.database.persistence.agent.converter.GatewayNodePersistenceConverter;


@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "gateway-nodes", name = "store", havingValue = "MYBATIS")
public class MybatisGatewayNodeRepository implements GatewayNodeRepository {
    private final GatewayNodeDao dao;
    private final GatewayNodePersistenceConverter converter;

    public MybatisGatewayNodeRepository(GatewayNodeDao dao, GatewayNodePersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
        public GatewayNode upsert(GatewayNode node) {
            dao.upsert(converter.toPo(node));
            return node;
        }

    @Override
        public Optional<GatewayNode> findById(String gatewayNodeId) {
            return Optional.ofNullable(dao.findById(gatewayNodeId)).map(converter::toNode);
        }

    @Override
        public List<GatewayNode> search(GatewayNodeQuery query) {
            return dao.search(query.getSiteId(), query.getRegion(), query.getZone(), query.getStatus() == null ? null : query.getStatus().name(), query.getLimit())
                    .stream()
                    .map(converter::toNode)
                    .toList();
        }

    @Override
        public int expireLeases(OffsetDateTime now) {
            return dao.expireLeases(now);
        }

    @Override
        public String mode() { return "MYBATIS"; }
}
