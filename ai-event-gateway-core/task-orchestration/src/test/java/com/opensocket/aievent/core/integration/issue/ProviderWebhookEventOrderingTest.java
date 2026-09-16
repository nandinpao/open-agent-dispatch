package com.opensocket.aievent.core.integration.issue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.integration.issue.webhook.*;

class ProviderWebhookEventOrderingTest {
    private final ProviderWebhookEventOrdering ordering = new ProviderWebhookEventOrdering();
    private final OffsetDateTime base = OffsetDateTime.parse("2026-07-27T02:00:00Z");

    @Test
    void explicitProviderSequenceShouldTakePrecedence() {
        assertThat(ordering.compare(state("event-10",10L,base),observation("event-11",11L,base.minusMinutes(5))))
                .isEqualTo(ProviderWebhookOrderingDecision.NEWER);
        assertThat(ordering.compare(state("event-10",10L,base),observation("event-09",9L,base.plusMinutes(5))))
                .isEqualTo(ProviderWebhookOrderingDecision.STALE_OR_AMBIGUOUS);
    }

    @Test
    void duplicateAndEqualTimestampShouldNeverOverwriteState() {
        assertThat(ordering.compare(state("event-10",10L,base),observation("event-10",10L,base)))
                .isEqualTo(ProviderWebhookOrderingDecision.DUPLICATE);
        assertThat(ordering.compare(state("event-a",null,base),observation("event-b",null,base)))
                .isEqualTo(ProviderWebhookOrderingDecision.STALE_OR_AMBIGUOUS);
    }

    private ExternalIssueObservedState state(String eventId,Long sequence,OffsetDateTime at) {
        return new ExternalIssueObservedState("tenant","state","connection","project","issue","KEY-1",null,null,"obs-old",
                eventId,sequence,null,at,null,"hash-old","{}","{}","diff","identity","schema",
                ExternalIssueConflictClassification.NONE,at,1,at);
    }
    private ExternalIssueObservation observation(String eventId,Long sequence,OffsetDateTime at) {
        return new ExternalIssueObservation("tenant","obs-new","inbox","connection","JIRA",eventId,sequence,null,"project","issue",
                "KEY-1","OPEN","{}","{}","hash-new",ExternalObservationNormalizer.PROFILE_VERSION,"identity","schema",at,at,"corr");
    }
}
