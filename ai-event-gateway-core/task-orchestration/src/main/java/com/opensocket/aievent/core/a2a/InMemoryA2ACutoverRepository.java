package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!prod")
@ConditionalOnProperty(prefix="a2a",name="cutover-store",havingValue="MEMORY",matchIfMissing=false)
public class InMemoryA2ACutoverRepository implements A2ACutoverRepository {
    private final Map<String,A2ACutoverState> states=new ConcurrentHashMap<>();
    private final List<A2ACutoverEvidence> evidence=new ArrayList<>();
    public InMemoryA2ACutoverRepository(){A2ACutoverState s=new A2ACutoverState();s.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));s.setUpdatedBy("BOOTSTRAP");states.put("INSTANCE",s);}
    public Optional<A2ACutoverState> find(String id){return Optional.ofNullable(states.get(id));}
    public synchronized A2ACutoverState save(A2ACutoverState state){states.put(state.getScopeId(),state);return state;}
    public synchronized A2ACutoverState saveExpectedVersion(A2ACutoverState state,long expected){A2ACutoverState old=states.get(state.getScopeId());if(old==null||old.getVersion()!=expected)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");states.put(state.getScopeId(),state);return state;}
    public synchronized A2ACutoverEvidence appendEvidence(A2ACutoverEvidence value){if(evidence.stream().noneMatch(e->e.getEvidenceId().equals(value.getEvidenceId())))evidence.add(value);return value;}
    public synchronized List<A2ACutoverEvidence> evidence(String scope,int limit){return evidence.stream().filter(e->scope.equals(e.getScopeId())).sorted(Comparator.comparing(A2ACutoverEvidence::getOccurredAt).reversed()).limit(Math.max(1,limit)).toList();}
    public String mode(){return "MEMORY";}
}
