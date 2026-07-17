package com.opensocket.aievent.gateway.netty.autoconfigure;

import com.opensocket.aievent.gateway.netty.admin.MachineAdminTokenAuthFilter;
import com.opensocket.aievent.gateway.netty.config.AdminCorsSupport;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.AdminWebMvcConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Auto-configuration entry point for projects that consume boot-starter.
 *
 * <p>The executable Netty app still uses component scanning because all modules share the
 * {@code com.opensocket.aievent.gateway.netty} base package. External Spring Boot applications,
 * however, usually have their own base package, so this auto-configuration makes the starter usable
 * without manually importing Gateway configuration classes.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(AdminProperties.class)
public class AiEventGatewaySpringBootAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper aiEventGatewayObjectMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean(AdminWebMvcConfig.class)
    public AdminWebMvcConfig aiEventGatewayAdminWebMvcConfig(AdminProperties adminProperties) {
        return new AdminWebMvcConfig(adminProperties);
    }

    @Bean
    @ConditionalOnMissingBean(AdminCorsSupport.class)
    public AdminCorsSupport aiEventGatewayAdminCorsSupport(AdminProperties adminProperties) {
        return new AdminCorsSupport(adminProperties);
    }

    @Bean
    @ConditionalOnMissingBean(MachineAdminTokenAuthFilter.class)
    public MachineAdminTokenAuthFilter aiEventGatewayMachineAdminTokenAuthFilter(AdminProperties adminProperties) {
        return new MachineAdminTokenAuthFilter(adminProperties);
    }
}
