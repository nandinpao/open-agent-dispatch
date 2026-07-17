package com.opensocket.aievent.core.gateway;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class GatewayNode {
    @ToString.Include
    private String gatewayNodeId;
    @ToString.Include
    private String nodeName;
    private String hostName;
    private String advertiseHost;
    private Integer httpPort;
    private Integer wsPort;
    private String region;
    private String zone;
    @ToString.Include
    private String siteId;
    private GatewayNodeStatus status = GatewayNodeStatus.ONLINE;
    private String version;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime registeredAt;
    private OffsetDateTime lastHeartbeatAt;
    private OffsetDateTime leaseExpiresAt;
    private OffsetDateTime updatedAt;
    public void setGatewayNodeId(String gatewayNodeId) { this.gatewayNodeId = gatewayNodeId; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public void setAdvertiseHost(String advertiseHost) { this.advertiseHost = advertiseHost; }
    public void setHttpPort(Integer httpPort) { this.httpPort = httpPort; }
    public void setWsPort(Integer wsPort) { this.wsPort = wsPort; }
    public void setRegion(String region) { this.region = region; }
    public void setZone(String zone) { this.zone = zone; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public void setStatus(GatewayNodeStatus status) { this.status = status == null ? GatewayNodeStatus.ONLINE : status; }
    public void setVersion(String version) { this.version = version; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }
    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public void setLeaseExpiresAt(OffsetDateTime leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
