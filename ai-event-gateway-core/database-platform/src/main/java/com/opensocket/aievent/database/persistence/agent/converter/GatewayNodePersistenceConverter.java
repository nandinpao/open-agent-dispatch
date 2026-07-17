package com.opensocket.aievent.database.persistence.agent.converter;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.gateway.GatewayNode;
import com.opensocket.aievent.core.gateway.GatewayNodeQuery;
import com.opensocket.aievent.core.gateway.GatewayNodeStatus;
import com.opensocket.aievent.database.persistence.agent.po.GatewayNodePo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "gateway-nodes", name = "store", havingValue = "MYBATIS")
public class GatewayNodePersistenceConverter {
    private final ObjectMapper objectMapper;

    public GatewayNodePersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GatewayNodePo toPo(GatewayNode node) {
            GatewayNodePo po = new GatewayNodePo();
            po.setGatewayNodeId(node.getGatewayNodeId());
            po.setNodeName(node.getNodeName());
            po.setHostName(node.getHostName());
            po.setAdvertiseHost(node.getAdvertiseHost());
            po.setHttpPort(node.getHttpPort());
            po.setWsPort(node.getWsPort());
            po.setRegion(node.getRegion());
            po.setZone(node.getZone());
            po.setSiteId(node.getSiteId());
            po.setStatus(node.getStatus() == null ? null : node.getStatus().name());
            po.setVersion(node.getVersion() == null || node.getVersion().isBlank() ? "unknown" : node.getVersion());
            po.setMetadataJson(toJson(node.getMetadata()));
            po.setRegisteredAt(node.getRegisteredAt());
            po.setLastHeartbeatAt(node.getLastHeartbeatAt());
            po.setLeaseExpiresAt(node.getLeaseExpiresAt());
            po.setUpdatedAt(node.getUpdatedAt());
            return po;
        }

    public GatewayNode toNode(GatewayNodePo po) {
            GatewayNode node = new GatewayNode();
            node.setGatewayNodeId(po.getGatewayNodeId());
            node.setNodeName(po.getNodeName());
            node.setHostName(po.getHostName());
            node.setAdvertiseHost(po.getAdvertiseHost());
            node.setHttpPort(po.getHttpPort());
            node.setWsPort(po.getWsPort());
            node.setRegion(po.getRegion());
            node.setZone(po.getZone());
            node.setSiteId(po.getSiteId());
            node.setStatus(po.getStatus() == null ? null : GatewayNodeStatus.valueOf(po.getStatus()));
            node.setVersion(po.getVersion() == null || po.getVersion().isBlank() ? "unknown" : po.getVersion());
            node.setMetadata(fromJson(po.getMetadataJson()));
            node.setRegisteredAt(po.getRegisteredAt());
            node.setLastHeartbeatAt(po.getLastHeartbeatAt());
            node.setLeaseExpiresAt(po.getLeaseExpiresAt());
            node.setUpdatedAt(po.getUpdatedAt());
            return node;
        }

    public String toJson(Object value) {
            try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
            catch (JacksonException ex) { return "{}"; }
        }

    public Map<String, Object> fromJson(String json) {
            try {
                if (json == null || json.isBlank()) return new LinkedHashMap<>();
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) { return new LinkedHashMap<>(); }
        }
}
