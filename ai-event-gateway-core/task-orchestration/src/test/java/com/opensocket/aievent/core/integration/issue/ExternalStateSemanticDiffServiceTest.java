package com.opensocket.aievent.core.integration.issue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.integration.issue.webhook.ExternalIssueConflictClassification;

class ExternalStateSemanticDiffServiceTest {
    private final ExternalStateSemanticDiffService service = new ExternalStateSemanticDiffService(new ObjectMapper());

    @Test
    void shouldRenderFieldLevelJsonPointerDiff() {
        var diff = service.compare("{\"status\":\"OPEN\",\"owner\":\"team-a\"}","desired-hash",null,
                "{\"status\":\"CLOSED\",\"owner\":\"team-a\"}","observed-hash",
                ExternalIssueConflictClassification.EXTERNAL_FIELD_CHANGED);

        assertThat(diff.changedFieldCount()).isEqualTo(1);
        assertThat(diff.comparisonMode()).isEqualTo("DESIRED_TO_OBSERVED");
        assertThat(diff.diffJson()).contains("/status", "OPEN", "CLOSED", "changedFieldCount");
    }
}
