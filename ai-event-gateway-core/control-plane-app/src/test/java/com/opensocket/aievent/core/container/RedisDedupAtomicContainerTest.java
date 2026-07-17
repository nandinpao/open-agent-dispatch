package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.dedup.EventDedupRedisProperties;
import com.opensocket.aievent.core.dedup.RedisDedupStateStore;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;

@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class RedisDedupAtomicContainerTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60));

    private LettuceConnectionFactory connectionFactory;

    @AfterEach
    void closeRedisConnectionFactory() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void concurrentTouchShouldAtomicallyIncrementOccurrenceCount() throws Exception {
        String fingerprint = "fp-" + UUID.randomUUID();
        RedisDedupStateStore store = store("aeg-test-" + UUID.randomUUID());

        int calls = 96;
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DedupDecision>> futures = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return store.touch(fingerprint, event("evt-" + index), Duration.ofMinutes(5), Duration.ofHours(1));
            }));
        }

        try {
            start.countDown();
            for (Future<DedupDecision> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(store.find(fingerprint)).isPresent()
                .get()
                .satisfies(state -> assertThat(state.getOccurrenceCount()).isEqualTo((long) calls));
    }

    private RedisDedupStateStore store(String keyPrefix) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(10))
                .shutdownTimeout(Duration.ofSeconds(1))
                .build();
        connectionFactory = new LettuceConnectionFactory(redisConfig, clientConfig);
        connectionFactory.setValidateConnection(true);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        assumeTrue(awaitRedisReady(redis), () -> "Redis Testcontainer did not become reachable at "
                + REDIS.getHost() + ":" + REDIS.getMappedPort(6379)
                + "; skipping Redis atomic container test instead of failing the full release gate on local Docker port readiness");
        EventDedupRedisProperties properties = new EventDedupRedisProperties();
        properties.setKeyPrefix(keyPrefix);
        return new RedisDedupStateStore(redis, properties);
    }

    private boolean awaitRedisReady(StringRedisTemplate redis) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 20; attempt++) {
            try {
                if (Boolean.TRUE.equals(redis.execute((org.springframework.data.redis.core.RedisCallback<Boolean>) connection -> {
                    String pong = connection.ping();
                    return "PONG".equalsIgnoreCase(pong);
                }))) {
                    return true;
                }
            } catch (RuntimeException ex) {
                lastFailure = ex;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        if (lastFailure != null) {
            System.err.println("Redis Testcontainer readiness check failed: " + lastFailure.getMessage());
        }
        return false;
    }

    private NormalizedEvent event(String eventId) {
        return new NormalizedEvent(
                eventId,
                "tenant-a",
                "MES",
                "EXTERNAL",
                "MES",
                "NO_TARGET_SYSTEM",
                "TNN",
                "TNN-FAB-01",
                "EQUIPMENT",
                "EQP-1001",
                "EQUIPMENT_ALARM",
                "TEMP_HIGH",
                "NO_REQUESTED_SKILL",
                "NO_HANDOFF_MODE",
                "NO_CORRELATION_ID",
                "NO_PARENT_TASK_ID",
                EventSeverity.HIGH,
                "chamber temperature over threshold",
                OffsetDateTime.now(ZoneOffset.UTC),
                Map.of());
    }
}
