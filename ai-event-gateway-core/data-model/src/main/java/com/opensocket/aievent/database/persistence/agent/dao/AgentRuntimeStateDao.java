package com.opensocket.aievent.database.persistence.agent.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.agent.po.AgentRuntimeCapabilityItemPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentRuntimeCapabilityProfilePo;
import com.opensocket.aievent.database.persistence.agent.po.AgentRuntimeLoadSnapshotPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentRuntimeDescriptorPo;

@Mapper
public interface AgentRuntimeStateDao {
    int upsertCapabilityProfile(@Param("profile") AgentRuntimeCapabilityProfilePo profile);
    int upsertRuntimeDescriptor(@Param("descriptor") AgentRuntimeDescriptorPo descriptor);
    int deleteCapabilityItems(@Param("agentId") String agentId);
    int insertCapabilityItems(@Param("items") List<AgentRuntimeCapabilityItemPo> items);
    int upsertLoadSnapshot(@Param("load") AgentRuntimeLoadSnapshotPo load);
    AgentRuntimeCapabilityProfilePo findCapabilityProfile(@Param("agentId") String agentId);
    AgentRuntimeDescriptorPo findRuntimeDescriptor(@Param("agentId") String agentId);
    List<AgentRuntimeCapabilityItemPo> findCapabilityItems(@Param("agentId") String agentId);
    AgentRuntimeLoadSnapshotPo findLoadSnapshot(@Param("agentId") String agentId);
}
