package com.opensocket.aievent.gateway.netty;

import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import com.opensocket.aievent.gateway.netty.config.ConnectionProtectionProperties;
import com.opensocket.aievent.gateway.netty.config.CoreDirectorySyncProperties;
import com.opensocket.aievent.gateway.netty.config.CoreForwardProperties;
import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.config.DeliveryRouterProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import com.opensocket.aievent.gateway.netty.config.TaskAssignmentProperties;
import com.opensocket.aievent.gateway.netty.authorization.CoreAgentAuthorizationProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Test-only Spring Boot entry point for admin-api module tests.
 *
 * <p>The production application class lives in gateway-app, which is not a
 * dependency of admin-api. Without this class, @SpringBootTest in this module
 * cannot discover a @SpringBootConfiguration when the module is built in isolation.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        GatewayProperties.class,
        NettyServerProperties.class,
        AgentProperties.class,
        AdminProperties.class,
        ClusterSyncProperties.class,
        AuditLogProperties.class,
        ConnectionProtectionProperties.class,
        CoreForwardProperties.class,
        CoreOutboundProperties.class,
        CoreDirectorySyncProperties.class,
        CoreTaskCallbackRelayProperties.class,
        TaskAssignmentProperties.class,
        DeliveryRouterProperties.class,
        CoreAgentAuthorizationProperties.class
})
class GatewayAdminApiTestApplication {
}
