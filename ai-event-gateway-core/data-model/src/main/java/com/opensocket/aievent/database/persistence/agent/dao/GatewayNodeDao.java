package com.opensocket.aievent.database.persistence.agent.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.agent.po.GatewayNodePo;

@Mapper
public interface GatewayNodeDao {
    int upsert(@Param("node") GatewayNodePo node);

    GatewayNodePo findById(@Param("gatewayNodeId") String gatewayNodeId);

    List<GatewayNodePo> search(
            @Param("siteId") String siteId,
            @Param("region") String region,
            @Param("zone") String zone,
            @Param("status") String status,
            @Param("limit") int limit);

    int expireLeases(@Param("now") OffsetDateTime now);
}
