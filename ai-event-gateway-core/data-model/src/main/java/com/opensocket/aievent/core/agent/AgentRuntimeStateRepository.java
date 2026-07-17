package com.opensocket.aievent.core.agent;

import java.util.List;
import java.util.Optional;

public interface AgentRuntimeStateRepository {
    void upsertFromSnapshot(AgentSnapshot agent);
    Optional<AgentRuntimeCapabilityProfile> findCapabilityProfile(String agentId);
    Optional<AgentRuntimeDescriptor> findRuntimeDescriptor(String agentId);
    List<AgentRuntimeCapabilityItem> findCapabilityItems(String agentId);
    Optional<AgentRuntimeLoadSnapshot> findLoadSnapshot(String agentId);
    String mode();
}
