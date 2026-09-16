package com.opensocket.aievent.core.integration.issue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.integration.issue.webhook.*;

/** Append-only, hash-chained audit timeline for every conflict decision and execution transition. */
@Component
public class ExternalIssueConflictLedger {
    private final ProviderWebhookReliabilityRepository repository;
    public ExternalIssueConflictLedger(ProviderWebhookReliabilityRepository repository){this.repository=repository;}

    public ExternalIssueConflictEvent append(ExternalIssueConflict conflict,ExternalIssueConflictEventType type,String actor,String reason,String metadataJson) {
        String previous=repository.latestConflictEvent(conflict.tenantId(),conflict.conflictId()).map(ExternalIssueConflictEvent::eventHash).orElse(null);
        OffsetDateTime at=OffsetDateTime.now(ZoneOffset.UTC);
        String metadata=metadataJson==null||metadataJson.isBlank()?"{}":metadataJson;
        String hash=ExternalObservationNormalizer.sha256(String.valueOf(previous)+"|"+conflict.tenantId()+"|"+conflict.conflictId()+"|"+type+"|"+String.valueOf(actor)+"|"+reason+"|"+metadata+"|"+at);
        return repository.appendConflictEvent(new ExternalIssueConflictEvent(conflict.tenantId(),"conflict-event-"+UUID.randomUUID(),conflict.conflictId(),type,actor,reason,metadata,previous,hash,at,conflict.correlationId()));
    }
}
