package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RedispatchDecisionEngineTest {
    @Test
    void shouldImmediatelyRedispatchOfferRejectButRetryWaitRuntimeFailure() {
        DispatchProperties properties = new DispatchProperties();
        RedispatchDecisionEngine engine = new RedispatchDecisionEngine(properties, new TaskRetryBackoffPolicy(properties));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RedispatchDecision reject = engine.decide(RedispatchFailureType.TEMPORARY_NETWORK_ERROR, 0, now);
        RedispatchDecision runtime = engine.decide(RedispatchFailureType.AGENT_DISCONNECTED, 1, now);

        assertThat(reject.action()).isEqualTo(RedispatchAction.IMMEDIATE_REDISPATCH);
        assertThat(reject.applyAgentCooldown()).isFalse();
        assertThat(runtime.action()).isEqualTo(RedispatchAction.RETRY_WAIT);
        assertThat(runtime.applyAgentCooldown()).isTrue();
        assertThat(runtime.nextRetryAt()).isAfter(now);
    }

    @Test
    void shouldDeadLetterWhenMaxRetryExceeded() {
        DispatchProperties properties = new DispatchProperties();
        properties.getRetry().setMaxAttempts(2);
        RedispatchDecisionEngine engine = new RedispatchDecisionEngine(properties, new TaskRetryBackoffPolicy(properties));

        RedispatchDecision decision = engine.decide(RedispatchFailureType.TEMPORARY_NETWORK_ERROR, 2, OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(decision.action()).isEqualTo(RedispatchAction.DEAD_LETTER);
        assertThat(decision.retryable()).isFalse();
    }
}
