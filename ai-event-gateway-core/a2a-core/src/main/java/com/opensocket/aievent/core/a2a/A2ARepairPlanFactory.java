package com.opensocket.aievent.core.a2a;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.HexFormat; import java.util.List;
/** Pure deterministic diagnosis -> repair-plan mapping. */
public final class A2ARepairPlanFactory {
 private A2ARepairPlanFactory(){}
 public static A2ARepairPlan create(A2AReconciliationCase value){
  A2AReconciliationCaseType type=parse(value.getCaseType());
  Mapping m=mapping(type);
  String evidence=first(value.getEvidenceSnapshotHash(),sha(first(value.getEvidenceSummary(),value.getReason(),value.getCaseId())));
  String impact=first(value.getImpactSummary(),"Repair is restricted to "+m.authority+" and must not bypass another authority.");
  long expected=value.getExpectedResourceVersion()>0?value.getExpectedResourceVersion():value.getVersion();
  String canonical=String.join("|",value.getTenantId(),value.getCaseId(),type.name(),m.diagnosis,m.authority.name(),m.action.name(),m.permission,String.valueOf(expected),evidence,impact);
  return new A2ARepairPlan(value.getCaseId(),type,m.diagnosis,m.authority,m.action,m.permission,expected,evidence,impact,m.preconditions,sha(canonical));
 }
 private static Mapping mapping(A2AReconciliationCaseType t){return switch(t){
  case CHILD_TASK_CREATION_UNCERTAIN -> new Mapping("CHILD_TASK_EVIDENCE_UNCERTAIN",A2ARepairAuthority.TASK_AUTHORITY,A2ARepairAction.RECONCILE_CHILD_TASK,"a2a.reconcile.task",List.of("Expected A2A version matches","No duplicate Child Task evidence"));
  case DISPATCH_UNCERTAIN,OUTBOX_STUCK -> new Mapping("DISPATCH_EVIDENCE_UNCERTAIN",A2ARepairAuthority.DISPATCH_AUTHORITY,A2ARepairAction.RECONCILE_DISPATCH,"a2a.reconcile.dispatch",List.of("Claim lease is current","Assignment evidence checked"));
  case HANDOFF_RELEASE_GAP -> new Mapping("HANDOFF_RELEASE_INCOMPLETE",A2ARepairAuthority.HANDOFF_CORE,A2ARepairAction.RETRY_HANDOFF_RELEASE,"a2a.reconcile.handoff",List.of("Snapshot hash valid","Approval and target binding valid"));
  case RESULT_PROCESSING_GAP -> new Mapping("RESULT_PROCESSING_INCOMPLETE",A2ARepairAuthority.A2A_CORE,A2ARepairAction.RECONCILE_RESULT,"a2a.reconcile.result",List.of("Canonical Result exists","Result evidence hash valid"));
  case PARENT_AGGREGATION_MISSING -> new Mapping("PARENT_AGGREGATION_INCOMPLETE",A2ARepairAuthority.A2A_CORE,A2ARepairAction.RECOMPUTE_PARENT_AGGREGATION,"a2a.reconcile.aggregation",List.of("Request-bound Policy Snapshot valid","Parent version matches"));
  case CANCELLATION_ACK_MISSING -> new Mapping("CANCELLATION_ACK_UNCERTAIN",A2ARepairAuthority.RUNTIME_AUTHORITY,A2ARepairAction.RECONCILE_CANCELLATION,"a2a.reconcile.cancellation",List.of("Result cutoff checked","Fencing binding checked"));
  case STATE_HISTORY_GAP -> new Mapping("STATE_HISTORY_GAP",A2ARepairAuthority.A2A_CORE,A2ARepairAction.REBUILD_STATE_HISTORY,"a2a.reconcile.history",List.of("Canonical authority records available"));
  default -> new Mapping("MANUAL_DIAGNOSIS_REQUIRED",A2ARepairAuthority.OPERATOR,A2ARepairAction.WAIT_HUMAN,"a2a.reconcile.manual",List.of("Human evidence review required"));};}
 private static A2AReconciliationCaseType parse(String v){try{return A2AReconciliationCaseType.valueOf(v);}catch(Exception e){return A2AReconciliationCaseType.UNKNOWN;}}
 private static String first(String...v){for(String x:v)if(x!=null&&!x.isBlank())return x;return "unknown";}
 private static String sha(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private record Mapping(String diagnosis,A2ARepairAuthority authority,A2ARepairAction action,String permission,List<String> preconditions){}
}
