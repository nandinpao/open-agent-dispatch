package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

/**
 * V40-5 real consumer proof: a new authenticated-local snapshot changes dispatch retry behavior
 * without reconstructing the business services; rollback is a newer revision and restores behavior.
 */
class V405DispatchRuntimeConsumerTest {
    @Test
    void shouldChangeActualRedispatchDecisionFromFiveToEightAndBackToFiveWithoutRestart() throws Exception {
        DispatchProperties startup = new DispatchProperties();
        startup.getRetry().setMaxAttempts(5);
        startup.getRetry().setInitialBackoff(Duration.ofSeconds(1));
        startup.getRetry().setMaxBackoff(Duration.ofSeconds(10));
        startup.getRetry().setJitterPercent(0);

        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        DispatchRuntimeConfigurationView view = new DispatchRuntimeConfigurationView(startup, values);
        TaskRetryBackoffPolicy backoff = new TaskRetryBackoffPolicy(startup);
        RedispatchDecisionEngine engine = new RedispatchDecisionEngine(startup, backoff);
        inject(backoff, "runtimeConfigurationView", view);
        inject(engine, "runtimeConfigurationView", view);

        OffsetDateTime now = OffsetDateTime.of(2026, 9, 18, 0, 0, 0, 0, ZoneOffset.UTC);
        assertThat(engine.decide(RedispatchFailureType.AGENT_DISCONNECTED, 5, now).action())
                .isEqualTo(RedispatchAction.DEAD_LETTER);

        registry.atomicSwap(snapshot("rev-8", 1,
                "{\"dispatch.retry.max-attempts\":8,\"dispatch.retry.initial-backoff\":\"PT2S\",\"dispatch.retry.max-backoff\":\"PT20S\",\"dispatch.retry.jitter-percent\":0}"));
        RedispatchDecision raisedBudget = engine.decide(RedispatchFailureType.AGENT_DISCONNECTED, 5, now);
        assertThat(raisedBudget.action()).isEqualTo(RedispatchAction.RETRY_WAIT);
        assertThat(raisedBudget.nextRetryAt()).isEqualTo(now.plusSeconds(20));
        assertThat(view.revisionId()).isEqualTo("rev-8");

        registry.atomicSwap(snapshot("rev-rollback-5", 2,
                "{\"dispatch.retry.max-attempts\":5,\"dispatch.retry.initial-backoff\":\"PT1S\",\"dispatch.retry.max-backoff\":\"PT10S\",\"dispatch.retry.jitter-percent\":0}"));
        RedispatchDecision rollback = engine.decide(RedispatchFailureType.AGENT_DISCONNECTED, 5, now);
        assertThat(rollback.action()).isEqualTo(RedispatchAction.DEAD_LETTER);
        assertThat(view.revisionId()).isEqualTo("rev-rollback-5");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime issued = OffsetDateTime.of(2026, 9, 18, 0, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-dispatch", RuntimeConfigurationSetKeys.DISPATCH_SYSTEM,
                revision, sequence, 2, issued, issued.plusHours(1), "CORE", payload,
                "post-verification-payload-hash", "post-verification-signature");
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
