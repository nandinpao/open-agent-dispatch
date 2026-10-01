package com.opensocket.aievent.core.configuration.distribution;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

/** Redis is a signed snapshot cache/pub-sub acceleration channel, never configuration authority. */
@Component
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationRedisPublisher {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RuntimeConfigurationDistributionProperties properties;

    public RuntimeConfigurationRedisPublisher(StringRedisTemplate redis,ObjectMapper objectMapper,RuntimeConfigurationDistributionProperties properties) {
        this.redis=redis; this.objectMapper=objectMapper; this.properties=properties;
    }

    public void publish(RuntimeConfigurationSnapshotEnvelope envelope) {
        String json=serialize(envelope);
        String key=properties.redisKeyPrefix()+":"+envelope.environment()+":"+envelope.configSetId()+":desired";
        redis.opsForValue().set(key,json,Duration.between(envelope.issuedAt(),envelope.expiresAt()));
        redis.convertAndSend(properties.redisChannel(),json);
    }

    public String serialize(RuntimeConfigurationSnapshotEnvelope envelope){try{return objectMapper.writeValueAsString(envelope);}catch(Exception e){throw new IllegalStateException("Unable to serialize signed configuration snapshot",e);}}
}
