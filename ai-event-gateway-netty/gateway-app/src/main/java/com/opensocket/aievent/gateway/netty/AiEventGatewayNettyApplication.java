package com.opensocket.aievent.gateway.netty;

import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import com.opensocket.aievent.gateway.netty.config.ConnectionProtectionProperties;
import com.opensocket.aievent.gateway.netty.config.CoreForwardProperties;
import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import com.opensocket.aievent.gateway.netty.config.CoreDirectorySyncProperties;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.config.DeliveryRouterProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import com.opensocket.aievent.gateway.netty.config.TaskAssignmentProperties;
import com.opensocket.aievent.gateway.netty.authorization.CoreAgentAuthorizationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for the AI Event Gateway Netty service. It enables configuration
 * binding and starts the HTTP, TCP, WebSocket, and cluster lifecycle components managed by
 * Spring.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({GatewayProperties.class, NettyServerProperties.class, AgentProperties.class, AdminProperties.class, ClusterSyncProperties.class, AuditLogProperties.class, ConnectionProtectionProperties.class, CoreForwardProperties.class, CoreOutboundProperties.class, CoreDirectorySyncProperties.class, CoreTaskCallbackRelayProperties.class, TaskAssignmentProperties.class, DeliveryRouterProperties.class, CoreAgentAuthorizationProperties.class})
public class AiEventGatewayNettyApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiEventGatewayNettyApplication.class, args);
    }
}
