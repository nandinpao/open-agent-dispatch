package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.agitg.redisson.config.RedissonAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.dedup.EventDedupRedisProperties;
import com.opensocket.aievent.core.dedup.RedissonDedupStateStore;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;

@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class SharedRedissonDedupAtomicContainerTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    private RedissonClient redissonClient;

    @AfterEach
    void closeRedisson() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void concurrentTouchShouldAtomicallyIncrementOccurrenceCountThroughSharedRedisson() throws Exception {
        String fingerprint = "fp-" + UUID.randomUUID();
        RedissonDedupStateStore store = store("aeg-redisson-test-" + UUID.randomUUID());

        int calls = 250;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DedupDecision>> futures = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return store.touch(fingerprint, event("evt-" + index), Duration.ofMinutes(5), Duration.ofHours(1));
            }));
        }

        start.countDown();
        for (Future<DedupDecision> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertThat(store.find(fingerprint)).isPresent()
                .get()
                .satisfies(state -> assertThat(state.getOccurrenceCount()).isEqualTo((long) calls));
    }

    private RedissonDedupStateStore store(String keyPrefix) {
        Config config = new Config();
        config.setCodec(new JsonJacksonCodec());
        config.useSingleServer()
                .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379))
                .setDatabase(0)
                .setTimeout(30000);
        redissonClient = Redisson.create(config);
        RedissonAccess access = new RedissonAccess(redissonClient, new ObjectMapper());
        EventDedupRedisProperties properties = new EventDedupRedisProperties();
        properties.setKeyPrefix(keyPrefix);
        return new RedissonDedupStateStore(access, properties);
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
