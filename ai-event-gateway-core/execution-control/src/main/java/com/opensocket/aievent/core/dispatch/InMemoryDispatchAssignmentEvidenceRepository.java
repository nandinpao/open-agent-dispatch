package com.opensocket.aievent.core.dispatch;
import java.util.*; import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Repository;
@Repository @Profile("!prod") @ConditionalOnProperty(prefix="dispatch",name="request-store",havingValue="MEMORY")
public class InMemoryDispatchAssignmentEvidenceRepository implements DispatchAssignmentEvidenceRepository {
 private final List<DispatchAssignmentEvidence> values=new CopyOnWriteArrayList<>();
 public synchronized DispatchAssignmentEvidence append(DispatchAssignmentEvidence e){
  return values.stream().filter(existing->Objects.equals(e.getDispatchRequestId(),existing.getDispatchRequestId())
    && e.getAttemptNo()==existing.getAttemptNo()&&Objects.equals(e.getEventType(),existing.getEventType()))
    .findFirst().orElseGet(()->{values.add(e);return e;});
 }
 public List<DispatchAssignmentEvidence> findByDispatchRequest(String id,int limit){return values.stream().filter(e->Objects.equals(id,e.getDispatchRequestId())).sorted(Comparator.comparing(DispatchAssignmentEvidence::getOccurredAt,Comparator.nullsLast(Comparator.reverseOrder()))).limit(Math.max(1,limit)).toList();}
 public boolean existsEvent(String id,int attempt,String type){return values.stream().anyMatch(e->Objects.equals(id,e.getDispatchRequestId())&&attempt==e.getAttemptNo()&&Objects.equals(type,e.getEventType()));}
 public String mode(){return "MEMORY";}
}
