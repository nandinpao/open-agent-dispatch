package com.opensocket.aievent.gateway.netty.authorization;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Agent authorization configuration properties for every Spring Boot context
 * that scans ai-event-gateway-core.
 *
 * <p>This is intentionally kept in the core module because AgentLifecycleService and
 * CoreAgentConnectionAuthorizationClient live in this module and can be loaded by
 * admin-api tests, the Netty application, or external starter consumers. Relying only
 * on each application class to list CoreAgentAuthorizationProperties is fragile and
 * caused admin-api ApplicationContext boot failures when the property bean was absent.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CoreAgentAuthorizationProperties.class)
public class AgentAuthorizationConfiguration {
}
