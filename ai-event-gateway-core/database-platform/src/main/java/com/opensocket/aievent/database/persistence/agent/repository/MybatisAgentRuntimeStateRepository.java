package com.opensocket.aievent.database.persistence.agent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityProfile;
import com.opensocket.aievent.core.agent.AgentRuntimeDescriptor;
import com.opensocket.aievent.core.agent.AgentRuntimeLoadSnapshot;
import com.opensocket.aievent.core.agent.AgentRuntimeStateRepository;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.database.persistence.agent.converter.AgentRuntimeStatePersistenceConverter;
import com.opensocket.aievent.database.persistence.agent.dao.AgentRuntimeStateDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "agent-directory", name = "store", havingValue = "MYBATIS")
public class MybatisAgentRuntimeStateRepository implements AgentRuntimeStateRepository {
    private final AgentRuntimeStateDao dao;
    private final AgentRuntimeStatePersistenceConverter converter;

    public MybatisAgentRuntimeStateRepository(AgentRuntimeStateDao dao, AgentRuntimeStatePersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public void upsertFromSnapshot(AgentSnapshot agent) {
        if (agent == null || agent.getAgentId() == null || agent.getAgentId().isBlank()) {
            return;
        }
        dao.upsertCapabilityProfile(converter.toCapabilityProfilePo(agent));
        dao.upsertRuntimeDescriptor(converter.toRuntimeDescriptorPo(agent));
        dao.deleteCapabilityItems(agent.getAgentId());
        var items = converter.toCapabilityItemPos(agent);
        if (!items.isEmpty()) {
            dao.insertCapabilityItems(items);
        }
        dao.upsertLoadSnapshot(converter.toRuntimeLoadPo(agent));
    }

    @Override
    public Optional<AgentRuntimeCapabilityProfile> findCapabilityProfile(String agentId) {
        return Optional.ofNullable(dao.findCapabilityProfile(agentId)).map(converter::toCapabilityProfile);
    }

    @Override
    public Optional<AgentRuntimeDescriptor> findRuntimeDescriptor(String agentId) {
        return Optional.ofNullable(dao.findRuntimeDescriptor(agentId)).map(converter::toRuntimeDescriptor);
    }

    @Override
    public List<AgentRuntimeCapabilityItem> findCapabilityItems(String agentId) {
        return dao.findCapabilityItems(agentId).stream().map(converter::toCapabilityItem).toList();
    }

    @Override
    public Optional<AgentRuntimeLoadSnapshot> findLoadSnapshot(String agentId) {
        return Optional.ofNullable(dao.findLoadSnapshot(agentId)).map(converter::toRuntimeLoad);
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }
}
