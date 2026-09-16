package com.opensocket.aievent.core.integration.issue;

import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.integration.issue.webhook.ExternalIssueObservation;
import com.opensocket.aievent.core.integration.issue.webhook.ExternalIssueObservedState;

/** Deterministic provider-event ordering: explicit sequence wins, then observed time; ambiguity never overwrites state. */
@Component
public class ProviderWebhookEventOrdering {
    public ProviderWebhookOrderingDecision compare(ExternalIssueObservedState previous,ExternalIssueObservation incoming) {
        if (Objects.equals(previous.lastAppliedProviderEventId(),incoming.providerEventId()))
            return ProviderWebhookOrderingDecision.DUPLICATE;
        Long oldSequence=previous.providerEventSequence(),newSequence=incoming.providerEventSequence();
        if (oldSequence!=null)
            return newSequence!=null&&newSequence>oldSequence?ProviderWebhookOrderingDecision.NEWER:ProviderWebhookOrderingDecision.STALE_OR_AMBIGUOUS;
        if (newSequence!=null) return ProviderWebhookOrderingDecision.NEWER;
        OffsetDateTime oldTime=first(previous.lastAppliedEventAt(),previous.observedAt(),previous.updatedAt());
        OffsetDateTime newTime=first(incoming.providerObservedAt(),incoming.createdAt());
        if (oldTime==null||newTime==null) return ProviderWebhookOrderingDecision.STALE_OR_AMBIGUOUS;
        return newTime.isAfter(oldTime)?ProviderWebhookOrderingDecision.NEWER:ProviderWebhookOrderingDecision.STALE_OR_AMBIGUOUS;
    }
    private OffsetDateTime first(OffsetDateTime... values){for(OffsetDateTime value:values)if(value!=null)return value;return null;}
}
