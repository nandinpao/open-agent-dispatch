package com.opensocket.aievent.core.integration.issue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.task.TaskRepository;

class IssueProjectionServiceSkipConvergenceTest {

    @Test
    void claimedHistoricalOutboxWithoutProviderShouldBecomeTerminalSkippedWithoutProviderRetry() {
        InMemoryIssueSyncRepository sync = new InMemoryIssueSyncRepository();
        IssueProjectionService service = new IssueProjectionService(
                mock(TaskRepository.class), mock(TaskIssueLinkRepository.class), sync);
        OffsetDateTime now = OffsetDateTime.now();
        IntegrationOutboxEntry claimed = new IntegrationOutboxEntry(
                "tenant-a", "outbox-1", "TASK", "task-1", "task-1", null, null,
                "connection-old", null, IntegrationOperationType.ISSUE_CREATE, "ISSUE_CREATE_REQUESTED",
                "{}", "hash", "idem-1", IntegrationOutboxStatus.CLAIMED, 100, 0, 8, now,
                "worker-1", now.plusMinutes(2), null, null, "corr-1", null, now, now, null);
        sync.saveOutbox(claimed);

        IntegrationOutboxEntry result = service.skipNotConfigured(
                "tenant-a", "outbox-1", "worker-1",
                IssueSyncReasonCode.ISSUE_PROVIDER_NOT_CONFIGURED.name(),
                "No governed Issue Provider is configured.");

        assertEquals(IntegrationOutboxStatus.SKIPPED, result.status());
        assertEquals(1, result.attemptCount());
        assertEquals(IssueSyncReasonCode.ISSUE_PROVIDER_NOT_CONFIGURED.name(), result.lastErrorCode());
        assertFalse(sync.listDueTenants(now.plusHours(1), 10).contains("tenant-a"));
    }
}
