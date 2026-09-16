package com.opensocket.aievent.core.iam.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opensocket.aievent.core.iam.persistence.dao.IamTokenDao;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MybatisMachineTokenRuntimeAdapterTest {
    @Mock IamTokenDao dao;
    @InjectMocks MybatisMachineTokenRuntimeAdapter adapter;

    @Test
    void machineOauthRateLimitComputesCleanupCutoffInJavaBeforeCallingSql() {
        Instant at = Instant.parse("2026-09-03T12:33:21.374Z");
        Instant windowStart = Instant.parse("2026-09-03T12:33:00Z");
        Instant cleanupBefore = Instant.parse("2026-09-03T12:23:00Z");
        when(dao.tryAcquireMachineOauthRateLimit("oauth:ip:test", windowStart, cleanupBefore, 120)).thenReturn(true);

        assertThat(adapter.tryAcquire("oauth:ip:test", 120, at)).isTrue();

        verify(dao).tryAcquireMachineOauthRateLimit("oauth:ip:test", windowStart, cleanupBefore, 120);
    }
}
