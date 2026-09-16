package com.opensocket.aievent.core.integration.issue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class IssueSyncWorkerConvergenceTest {

    @Test
    void noSupportingProviderShouldConvergeAsSkippedInsteadOfRetryingOrThrowing() {
        IssueSyncRepository repository = mock(IssueSyncRepository.class);
        IssueProjectionService service = mock(IssueProjectionService.class);
        IntegrationOutboxEntry item = outbox();
        when(repository.listDueTenants(any(), eq(10))).thenReturn(List.of("tenant-a"));
        when(service.claimDue(eq("tenant-a"), anyString(), eq(10))).thenReturn(List.of(item));

        IssueSyncWorker worker = new IssueSyncWorker(repository, service, List.of());
        int processed = worker.runOnce(10);

        verify(service).skipNotConfigured(eq("tenant-a"), eq("outbox-1"), anyString(),
                eq(IssueSyncReasonCode.ISSUE_PROVIDER_NOT_CONFIGURED.name()), anyString());
        verify(service, never()).fail(anyString(), anyString(), anyString(), any());
        assertEquals(1, processed);
    }

    @Test
    void unavailableFallbackGatewayShouldAlsoSkipWithoutExecutingProviderCall() {
        IssueSyncRepository repository = mock(IssueSyncRepository.class);
        IssueProjectionService service = mock(IssueProjectionService.class);
        IssueSyncProviderGateway gateway = mock(IssueSyncProviderGateway.class);
        IntegrationOutboxEntry item = outbox();
        when(repository.listDueTenants(any(), eq(10))).thenReturn(List.of("tenant-a"));
        when(service.claimDue(eq("tenant-a"), anyString(), eq(10))).thenReturn(List.of(item));
        when(gateway.supports(item)).thenReturn(true);
        when(gateway.mode()).thenReturn("UNAVAILABLE_FAIL_CLOSED");

        IssueSyncWorker worker = new IssueSyncWorker(repository, service, List.of(gateway));
        worker.runOnce(10);

        verify(service).skipNotConfigured(eq("tenant-a"), eq("outbox-1"), anyString(),
                eq(IssueSyncReasonCode.ISSUE_PROVIDER_NOT_CONFIGURED.name()), anyString());
        verify(gateway, never()).execute(any());
    }

    private IntegrationOutboxEntry outbox() {
        OffsetDateTime now = OffsetDateTime.now();
        return new IntegrationOutboxEntry("tenant-a", "outbox-1", "TASK", "task-1", "task-1", null, null,
                null, null, IntegrationOperationType.ISSUE_CREATE, "ISSUE_CREATE_REQUESTED", "{}", "hash", "idem-1",
                IntegrationOutboxStatus.CLAIMED, 100, 0, 8, now, "worker-old", now.plusMinutes(2), null, null,
                "corr-1", null, now, now, null);
    }
}
