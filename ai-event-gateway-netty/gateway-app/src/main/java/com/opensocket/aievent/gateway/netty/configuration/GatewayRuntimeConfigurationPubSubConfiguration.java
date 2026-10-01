package com.opensocket.aievent.gateway.netty.configuration;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;

/** Redis is only an acceleration signal. The listener always re-reads desired state from Core authority. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="gateway.runtime-configuration",name="pubsub-enabled",havingValue="true")
public class GatewayRuntimeConfigurationPubSubConfiguration {
    @Bean("gatewayRuntimeConfigurationRedisListenerContainer")
    RedisMessageListenerContainer gatewayRuntimeConfigurationRedisListenerContainer(RedisConnectionFactory connectionFactory,
            GatewayRuntimeConfigurationReconciler reconciler,GatewayRuntimeConfigurationProperties properties,
            GatewayProperties gateway,ObjectMapper objectMapper) {
        RedisMessageListenerContainer container=new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message,pattern)->{
            try {
                var json=objectMapper.readTree(new String(message.getBody(),StandardCharsets.UTF_8));
                String configSetId=json.path("configSetId").asText("");
                String environment=json.path("environment").asText("");
                if(configSetId.isBlank()||!environment.equalsIgnoreCase(gateway.environment())) return;
                if(!properties.configSetIds().isEmpty()&&!properties.configSetIds().contains(configSetId)) return;
                reconciler.reconcileOne(configSetId);
            } catch(Exception ignored) {
                // Pub/Sub is best-effort acceleration. Periodic authority reconciliation remains the healing path.
            }
        },new ChannelTopic(properties.redisChannel()));
        return container;
    }
}
