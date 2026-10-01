package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.admin.dto.GatewayStatusResponse;
import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.AgentRuntimeConfigurationView;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeRegistry;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeStatus;
import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionRegistry;
import com.opensocket.aievent.gateway.netty.runtime.NettyOperationalRuntimeConfigurationView;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketClientType;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionRegistry;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Aggregates local Netty transport runtime status. It deliberately excludes task lifecycle state;
 * task state belongs to ai-event-gateway-core / control-plane.
 */
@Service
public class GatewayStatusService {

    private final GatewayProperties gatewayProperties;
    private final NettyServerProperties nettyServerProperties;
    private final AgentProperties agentProperties;
    private final AgentRuntimeConfigurationView agentRuntimeConfiguration;
    private final TcpConnectionRegistry tcpConnectionRegistry;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final AgentRegistry agentRegistry;
    private final ClusterNodeRegistry clusterNodeRegistry;
    private final NettyOperationalRuntimeConfigurationView nettyRuntimeConfiguration;

    public GatewayStatusService(
            GatewayProperties gatewayProperties,
            NettyServerProperties nettyServerProperties,
            AgentProperties agentProperties,
            AgentRuntimeConfigurationView agentRuntimeConfiguration,
            TcpConnectionRegistry tcpConnectionRegistry,
            WebSocketSessionRegistry webSocketSessionRegistry,
            AgentRegistry agentRegistry,
            ClusterNodeRegistry clusterNodeRegistry,
            NettyOperationalRuntimeConfigurationView nettyRuntimeConfiguration
    ) {
        this.gatewayProperties = gatewayProperties;
        this.nettyServerProperties = nettyServerProperties;
        this.agentProperties = agentProperties;
        this.agentRuntimeConfiguration = agentRuntimeConfiguration == null ? new AgentRuntimeConfigurationView(agentProperties) : agentRuntimeConfiguration;
        this.tcpConnectionRegistry = tcpConnectionRegistry;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.agentRegistry = agentRegistry;
        this.clusterNodeRegistry = clusterNodeRegistry;
        this.nettyRuntimeConfiguration = nettyRuntimeConfiguration;
    }

    public GatewayStatusResponse getStatus() {
        var tcp = nettyServerProperties.tcp();
        var websocket = nettyServerProperties.websocket();
        var cluster = nettyServerProperties.cluster();

        return new GatewayStatusResponse(
                gatewayProperties.nodeId(),
                gatewayProperties.environment(),
                gatewayProperties.version(),
                gatewayProperties.releaseLabel(),
                gatewayProperties.artifactRevision(),
                "UP",
                tcp.enabled(),
                tcp.host(),
                tcp.port(),
                tcpConnectionRegistry.countActive(),
                websocket.enabled(),
                websocket.host(),
                websocket.port(),
                webSocketSessionRegistry.countActive(),
                webSocketSessionRegistry.countActiveByType(WebSocketClientType.AGENT),
                webSocketSessionRegistry.countActiveByType(WebSocketClientType.ADMIN),
                agentRegistry.count(),
                agentRegistry.countByStatus(AgentStatus.IDLE),
                agentRegistry.countByStatus(AgentStatus.BUSY),
                agentRegistry.countByStatus(AgentStatus.OFFLINE),
                agentRegistry.countByStatus(AgentStatus.TIMEOUT),
                agentRuntimeConfiguration.heartbeatTimeoutSeconds(),
                cluster.enabled(),
                cluster.safeUdpHost(),
                cluster.safeUdpPort(),
                cluster.safeBroadcastHost(),
                cluster.safeBroadcastPort(),
                clusterNodeRegistry.count(),
                clusterNodeRegistry.countByStatus(ClusterNodeStatus.SELF) + clusterNodeRegistry.countByStatus(ClusterNodeStatus.ONLINE),
                clusterNodeRegistry.countByStatus(ClusterNodeStatus.SUSPECT),
                clusterNodeRegistry.countByStatus(ClusterNodeStatus.OFFLINE),
                nettyRuntimeConfiguration.clusterHeartbeatIntervalMs(),
                nettyRuntimeConfiguration.clusterSuspectTimeoutMs(),
                nettyRuntimeConfiguration.clusterOfflineTimeoutMs(),
                OffsetDateTime.now()
        );
    }
}
