package com.opensocket.aievent.database.persistence.agent.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class GatewayNodePo {
    private String gatewayNodeId;
    private String nodeName;
    private String hostName;
    private String advertiseHost;
    private Integer httpPort;
    private Integer wsPort;
    private String region;
    private String zone;
    private String siteId;
    private String status;
    private String version;
    private String metadataJson;
    private OffsetDateTime registeredAt;
    private OffsetDateTime lastHeartbeatAt;
    private OffsetDateTime leaseExpiresAt;
    private OffsetDateTime updatedAt;
}
