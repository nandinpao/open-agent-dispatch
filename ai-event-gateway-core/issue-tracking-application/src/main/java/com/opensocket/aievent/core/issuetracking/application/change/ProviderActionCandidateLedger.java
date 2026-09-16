package com.opensocket.aievent.core.issuetracking.application.change;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.*; import java.util.*; import java.util.HexFormat; import org.springframework.stereotype.Service;
import com.opensocket.aievent.core.issuetracking.change.*;
@Service public class ProviderActionCandidateLedger {
 private final ExternalChangeGovernanceRepository repository; public ProviderActionCandidateLedger(ExternalChangeGovernanceRepository repository){this.repository=repository;}
 public ProviderActionCandidateEvent append(ProviderActionCandidate c,ProviderActionCandidateEventType type,String actor,String reason,String metadata){String previous=repository.latestCandidateEvent(c.tenantId(),c.candidateId()).map(ProviderActionCandidateEvent::eventHash).orElse("");OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);String id="candidate-event-"+UUID.randomUUID();String hash=sha(previous+"\n"+c.tenantId()+"\n"+c.candidateId()+"\n"+type+"\n"+safe(actor)+"\n"+reason+"\n"+safe(metadata)+"\n"+now);return repository.appendCandidateEvent(new ProviderActionCandidateEvent(c.tenantId(),id,c.candidateId(),type,actor,reason,metadata,previous,hash,now,c.correlationId()));}
 private String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}} private String safe(String v){return v==null?"":v;}
}
