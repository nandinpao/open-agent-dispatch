package com.opensocket.aievent.core.a2a;
import java.util.List;
public record A2AReconciliationDetail(A2AReconciliationCase reconciliationCase,A2ARepairPlan repairPlan,List<A2AReconciliationEvidence> evidence){
 public A2AReconciliationDetail{evidence=evidence==null?List.of():List.copyOf(evidence);}
}
