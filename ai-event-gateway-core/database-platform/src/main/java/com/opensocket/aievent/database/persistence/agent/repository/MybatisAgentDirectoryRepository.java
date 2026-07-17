package com.opensocket.aievent.database.persistence.agent.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.agent.AgentDirectoryRepository;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.database.persistence.agent.dao.AgentDirectoryDao;
import com.opensocket.aievent.database.persistence.agent.po.AgentSnapshotPo;
import com.opensocket.aievent.database.persistence.agent.converter.AgentDirectoryPersistenceConverter;


@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "agent-directory", name = "store", havingValue = "MYBATIS")
public class MybatisAgentDirectoryRepository implements AgentDirectoryRepository {
    private final AgentDirectoryDao dao;
    private final AgentDirectoryPersistenceConverter converter;

    public MybatisAgentDirectoryRepository(AgentDirectoryDao dao, AgentDirectoryPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
        public AgentSnapshot upsert(AgentSnapshot agent) {
            dao.upsert(converter.toPo(agent));
            return agent;
        }

    @Override
        public Optional<AgentSnapshot> findById(String agentId) {
            return Optional.ofNullable(dao.findById(agentId)).map(converter::toAgent);
        }

    @Override
        public List<AgentSnapshot> search(AgentQuery query) {
            return dao.search(query.getSiteId(), query.getOwnerGatewayNodeId(), query.getStatus() == null ? null : query.getStatus().name(), query.isAssignableOnly(), query.getRequiredCapabilities(), query.getLimit())
                    .stream().map(converter::toAgent).toList();
        }

    @Override
        public void updateStatus(String agentId, AgentStatus status) {
            dao.updateStatus(agentId, status.name());
        }

    @Override
        public boolean reserveCapacity(String agentId) {
            return dao.reserveCapacity(agentId) == 1;
        }

    @Override
        public boolean releaseCapacity(String agentId) {
            return dao.releaseCapacity(agentId) == 1;
        }

    @Override
        public int markByGatewayNodeId(String gatewayNodeId, AgentStatus status, OffsetDateTime disconnectedAt) {
            return dao.markByGatewayNodeId(gatewayNodeId, status.name(), disconnectedAt);
        }

    @Override
        public int expireLeases(OffsetDateTime now) {
            return dao.expireLeases(now);
        }

    @Override
        public String mode() { return "MYBATIS"; }
}
