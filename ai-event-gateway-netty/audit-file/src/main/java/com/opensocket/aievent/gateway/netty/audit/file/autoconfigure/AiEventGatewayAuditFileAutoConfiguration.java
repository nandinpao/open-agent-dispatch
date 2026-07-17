package com.opensocket.aievent.gateway.netty.audit.file.autoconfigure;

import com.opensocket.aievent.gateway.netty.admin.audit.FileAuditEventPersistencePort;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/** Auto-configuration for the FILE audit sink adapter. */
@AutoConfiguration(afterName = "com.opensocket.aievent.gateway.netty.audit.autoconfigure.AiEventGatewayAuditAutoConfiguration")
@EnableConfigurationProperties(AuditLogProperties.class)
public class AiEventGatewayAuditFileAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FileAuditEventPersistencePort.class)
    @ConditionalOnProperty(prefix = "audit", name = "sink", havingValue = "FILE")
    public FileAuditEventPersistencePort aiEventGatewayFileAuditEventPersistencePort(
            AuditLogProperties auditLogProperties,
            ObjectMapper objectMapper
    ) {
        return new FileAuditEventPersistencePort(auditLogProperties, objectMapper);
    }
}
