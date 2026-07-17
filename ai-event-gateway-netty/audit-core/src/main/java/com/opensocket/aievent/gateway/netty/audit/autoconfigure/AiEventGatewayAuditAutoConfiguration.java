package com.opensocket.aievent.gateway.netty.audit.autoconfigure;

import com.opensocket.aievent.gateway.netty.admin.AdminEventStore;
import com.opensocket.aievent.gateway.netty.admin.audit.AuditEventPersistencePort;
import com.opensocket.aievent.gateway.netty.admin.audit.NoopAuditEventPersistencePort;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Auto-configuration for the reusable Admin audit event store and default NOOP audit sink. */
@AutoConfiguration
@EnableConfigurationProperties(AuditLogProperties.class)
public class AiEventGatewayAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditEventPersistencePort.class)
    @ConditionalOnProperty(prefix = "audit", name = "sink", havingValue = "NOOP", matchIfMissing = true)
    public AuditEventPersistencePort aiEventGatewayNoopAuditEventPersistencePort() {
        return new NoopAuditEventPersistencePort();
    }

    @Bean
    @ConditionalOnMissingBean(AdminEventStore.class)
    public AdminEventStore aiEventGatewayAdminEventStore(
            GatewayProperties gatewayProperties,
            AdminProperties adminProperties,
            AuditLogProperties auditLogProperties,
            AuditEventPersistencePort auditEventPersistencePort
    ) {
        return new AdminEventStore(gatewayProperties, adminProperties, auditLogProperties, auditEventPersistencePort);
    }
}
