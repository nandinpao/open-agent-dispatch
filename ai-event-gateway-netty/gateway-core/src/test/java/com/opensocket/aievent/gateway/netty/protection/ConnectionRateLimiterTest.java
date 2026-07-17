package com.opensocket.aievent.gateway.netty.protection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRateLimiterTest {

    @Test
    void shouldRejectWhenFixedWindowLimitIsExceeded() {
        var limiter = new ConnectionRateLimiter();

        assertThat(limiter.tryAcquire("tcp:/127.0.0.1", 2)).isTrue();
        assertThat(limiter.tryAcquire("tcp:/127.0.0.1", 2)).isTrue();
        assertThat(limiter.tryAcquire("tcp:/127.0.0.1", 2)).isFalse();
    }

    @Test
    void shouldMaintainIndependentCountersPerKey() {
        var limiter = new ConnectionRateLimiter();

        assertThat(limiter.tryAcquire("tcp:/127.0.0.1", 1)).isTrue();
        assertThat(limiter.tryAcquire("ws:/127.0.0.1", 1)).isTrue();
        assertThat(limiter.tryAcquire("tcp:/127.0.0.1", 1)).isFalse();
    }
}
