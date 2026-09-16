package com.opensocket.aievent.core.integration.issue;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.opensocket.aievent.core.integration.issue.projection.*;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix="issue-projection",name="store",havingValue="MEMORY",matchIfMissing=true)
public class InMemoryIssueRelayRepository implements IssueRelayRepository {
    private final Map<String,CrossProjectIssueRelay> relays=new ConcurrentHashMap<>();
    private final Map<String,IssueRelayAttempt> attempts=new ConcurrentHashMap<>();
    private String key(String tenant,String id){return tenant+":"+id;}
    public CrossProjectIssueRelay saveRelay(CrossProjectIssueRelay value){relays.put(key(value.tenantId(),value.relayId()),value);return value;}
    public Optional<CrossProjectIssueRelay> findRelay(String tenant,String id){return Optional.ofNullable(relays.get(key(tenant,id)));}
    public Optional<CrossProjectIssueRelay> findRelayByIdempotencyKey(String tenant,String idempotencyKey){return relays.values().stream().filter(v->tenant.equals(v.tenantId())&&idempotencyKey.equals(v.idempotencyKey())).findFirst();}
    public List<CrossProjectIssueRelay> listRelays(String tenant,String taskId,int limit){return relays.values().stream().filter(v->tenant.equals(v.tenantId())&&(taskId==null||taskId.isBlank()||taskId.equals(v.sourceTaskId())||taskId.equals(v.targetTaskId())||taskId.equals(v.rootTaskId()))).sorted(Comparator.comparing(CrossProjectIssueRelay::createdAt).reversed()).limit(limit).toList();}
    public List<CrossProjectIssueRelay> listDueRelays(OffsetDateTime now,int limit){return relays.values().stream().filter(v->v.relayState()==IssueRelayState.FAILED_RETRYABLE&&v.nextRetryAt()!=null&&!v.nextRetryAt().isAfter(now)).sorted(Comparator.comparing(CrossProjectIssueRelay::nextRetryAt)).limit(limit).toList();}
    public IssueRelayAttempt saveRelayAttempt(IssueRelayAttempt value){attempts.put(key(value.tenantId(),value.attemptId()),value);return value;}
    public List<IssueRelayAttempt> listRelayAttempts(String tenant,String relayId,int limit){return attempts.values().stream().filter(v->tenant.equals(v.tenantId())&&relayId.equals(v.relayId())).sorted(Comparator.comparing(IssueRelayAttempt::startedAt)).limit(limit).toList();}
    public String mode(){return "MEMORY";}
}
