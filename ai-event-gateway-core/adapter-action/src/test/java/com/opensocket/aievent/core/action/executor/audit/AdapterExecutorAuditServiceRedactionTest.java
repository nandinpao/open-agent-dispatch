package com.opensocket.aievent.core.action.executor.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.action.executor.AdapterSecretRedactor;

class AdapterExecutorAuditServiceRedactionTest {

    @Test
    void shouldRedactPayloadSnapshotAndMessageBeforePersistingAuditRecord() {
        InMemoryAdapterExecutorAuditRepository repository = new InMemoryAdapterExecutorAuditRepository();
        AdapterExecutorAuditService service = new AdapterExecutorAuditService(repository, new AdapterActionExecutionProperties());
        AdapterAction action = new AdapterAction();
        action.setActionId("action-redaction-1");
        action.setAdapterType(AdapterType.ISSUE_TRACKING);
        action.setPayload(Map.of(
                "vendor", "GITLAB",
                "privateToken", "gitlab-secret-token",
                "nested", Map.of("redmineApiKey", "redmine-secret-token")));

        service.record(action,
                AdapterActionStatus.PENDING,
                AdapterActionStatus.RETRY_WAITING,
                AdapterExecutionResult.retryableFailure("gitlab", "PRIVATE-TOKEN=gitlab-secret-token"),
                null);

        AdapterExecutorAuditRecord record = repository.findByActionId("action-redaction-1", 1).get(0);
        assertThat(record.getMessage()).contains(AdapterSecretRedactor.REDACTED).doesNotContain("gitlab-secret-token");
        assertThat(record.getPayloadSnapshot().toString())
                .contains(AdapterSecretRedactor.REDACTED)
                .doesNotContain("gitlab-secret-token", "redmine-secret-token");
        assertThat(record.getPayloadSnapshot().get("vendor")).isEqualTo("GITLAB");
    }
}
