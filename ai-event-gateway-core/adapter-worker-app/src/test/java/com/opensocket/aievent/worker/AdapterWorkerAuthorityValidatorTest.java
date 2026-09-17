package com.opensocket.aievent.worker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class AdapterWorkerAuthorityValidatorTest {
    @Test
    void issueTrackingTypeIsRejectedAtWorkerStartup() {
        AdapterWorkerProperties properties = new AdapterWorkerProperties();
        properties.setAdapterTypes(Set.of("MCP", "ISSUE_TRACKING"));

        assertThatThrownBy(() -> new AdapterWorkerAuthorityValidator(properties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ISSUE_TRACKING_EXTERNAL_WORKER_FORBIDDEN_CORE_GOVERNED_AUTHORITY");
    }

    @Test
    void legacyIssueEndpointIsRejectedEvenWhenTypeListIsMcpOnly() {
        AdapterWorkerProperties properties = new AdapterWorkerProperties();
        properties.setAdapterTypes(Set.of("MCP"));
        properties.setIssueEndpointUrl("https://legacy-issue-executor.example/internal");

        assertThatThrownBy(() -> new AdapterWorkerAuthorityValidator(properties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADAPTER_WORKER_ISSUE_ENDPOINT_URL");
    }
}
