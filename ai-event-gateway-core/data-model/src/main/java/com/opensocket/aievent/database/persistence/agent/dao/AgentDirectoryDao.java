package com.opensocket.aievent.database.persistence.agent.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.agent.po.AgentSnapshotPo;

@Mapper
public interface AgentDirectoryDao {
    int upsert(@Param("agent") AgentSnapshotPo agent);
    AgentSnapshotPo findById(@Param("agentId") String agentId);
    List<AgentSnapshotPo> search(@Param("siteId") String siteId,
                                  @Param("ownerGatewayNodeId") String ownerGatewayNodeId,
                                  @Param("status") String status,
                                  @Param("assignableOnly") boolean assignableOnly,
                                  @Param("requiredCapabilities") List<String> requiredCapabilities,
                                  @Param("limit") int limit);
    int updateStatus(@Param("agentId") String agentId, @Param("status") String status);
    int reserveCapacity(@Param("agentId") String agentId);
    int releaseCapacity(@Param("agentId") String agentId);
    int markByGatewayNodeId(@Param("gatewayNodeId") String gatewayNodeId,
                            @Param("status") String status,
                            @Param("disconnectedAt") OffsetDateTime disconnectedAt);
    int expireLeases(@Param("now") OffsetDateTime now);
}
