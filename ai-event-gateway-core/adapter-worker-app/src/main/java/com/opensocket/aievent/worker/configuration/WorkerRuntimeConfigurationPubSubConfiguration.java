package com.opensocket.aievent.worker.configuration;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

/** Best-effort Redis signal; desired values are still fetched from Core authority before apply. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="adapter-worker.runtime-configuration",name="pubsub-enabled",havingValue="true")
public class WorkerRuntimeConfigurationPubSubConfiguration {
    @Bean("workerRuntimeConfigurationRedisListenerContainer")
    RedisMessageListenerContainer workerRuntimeConfigurationRedisListenerContainer(RedisConnectionFactory connectionFactory,
            WorkerRuntimeConfigurationReconciler reconciler,WorkerRuntimeConfigurationProperties properties,ObjectMapper objectMapper,
            @Value("${opendispatch.environment}") String environment) {
        RedisMessageListenerContainer container=new RedisMessageListenerContainer();container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message,pattern)->{try{var json=objectMapper.readTree(new String(message.getBody(),StandardCharsets.UTF_8));String id=json.path("configSetId").asText("");String env=json.path("environment").asText("");if(id.isBlank()||!env.equalsIgnoreCase(environment)||(!properties.configSetIds().isEmpty()&&!properties.configSetIds().contains(id)))return;reconciler.reconcileOne(id);}catch(Exception ignored){/* periodic healing owns correctness */}},new ChannelTopic(properties.redisChannel()));
        return container;
    }
}
