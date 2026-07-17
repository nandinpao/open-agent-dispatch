#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVAC="${JAVAC:-$(command -v javac || true)}"
JAVA="${JAVA:-$(command -v java || true)}"
if [[ -z "$JAVAC" || -z "$JAVA" ]]; then
  echo "[WARN] Java toolchain unavailable; P9 effectful Action runtime harness skipped"
  exit 0
fi
TMP="$(mktemp -d -t p9-action-runtime-XXXXXX)"
trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/classes"; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }

w org/springframework/stereotype/Service.java <<'JAVA'
package org.springframework.stereotype; public @interface Service {}
JAVA
w org/springframework/transaction/annotation/Transactional.java <<'JAVA'
package org.springframework.transaction.annotation; public @interface Transactional { Propagation propagation() default Propagation.REQUIRED; }
JAVA
w org/springframework/transaction/annotation/Propagation.java <<'JAVA'
package org.springframework.transaction.annotation; public enum Propagation { REQUIRED, REQUIRES_NEW }
JAVA
w org/springframework/beans/factory/annotation/Autowired.java <<'JAVA'
package org.springframework.beans.factory.annotation; public @interface Autowired {}
JAVA
w com/fasterxml/jackson/databind/ObjectMapper.java <<'JAVA'
package com.fasterxml.jackson.databind; import java.nio.charset.StandardCharsets; public class ObjectMapper { public byte[] writeValueAsBytes(Object value){ return String.valueOf(value).getBytes(StandardCharsets.UTF_8); } }
JAVA
w com/opensocket/aievent/core/task/TaskStatus.java <<'JAVA'
package com.opensocket.aievent.core.task; public enum TaskStatus { QUEUED, SUCCEEDED, FAILED, CANCELLED; public boolean isSucceeded(){ return this==SUCCEEDED; } public boolean isTerminal(){ return this==SUCCEEDED||this==FAILED||this==CANCELLED; } }
JAVA
w com/opensocket/aievent/core/task/TaskType.java <<'JAVA'
package com.opensocket.aievent.core.task; public enum TaskType { INCIDENT_RESPONSE }
JAVA
w com/opensocket/aievent/core/task/TaskPriority.java <<'JAVA'
package com.opensocket.aievent.core.task; public enum TaskPriority { LOW, MEDIUM, HIGH, CRITICAL }
JAVA
w com/opensocket/aievent/core/task/TaskRecord.java <<'JAVA'
package com.opensocket.aievent.core.task; import java.time.*; import java.util.*;
public class TaskRecord {
 private String taskId,incidentId,sourceEventId,sourceSystem,eventStage,originSourceSystem,targetSystem,taskTypeCode,tenantId,siteId,plantId,objectType,objectId,eventType,requestedSkill,correlationId,parentTaskId,matchedFlowId,matchedRuleId,routingPath,routingPolicy,createdReason,lifecycleReason,externalExecutionKey;
 private TaskType taskType; private TaskStatus status; private TaskPriority priority; private List<String> requiredCapabilities=List.of(); private OffsetDateTime createdAt,updatedAt,terminalAt;
 public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;} public String getIncidentId(){return incidentId;} public void setIncidentId(String v){incidentId=v;} public String getSourceEventId(){return sourceEventId;} public void setSourceEventId(String v){sourceEventId=v;} public String getSourceSystem(){return sourceSystem;} public void setSourceSystem(String v){sourceSystem=v;} public String getEventStage(){return eventStage;} public void setEventStage(String v){eventStage=v;} public String getOriginSourceSystem(){return originSourceSystem;} public void setOriginSourceSystem(String v){originSourceSystem=v;} public String getTargetSystem(){return targetSystem;} public void setTargetSystem(String v){targetSystem=v;} public TaskType getTaskType(){return taskType;} public void setTaskType(TaskType v){taskType=v;} public String getTaskTypeCode(){return taskTypeCode;} public void setTaskTypeCode(String v){taskTypeCode=v;} public TaskStatus getStatus(){return status;} public void setStatus(TaskStatus v){status=v;} public TaskPriority getPriority(){return priority;} public void setPriority(TaskPriority v){priority=v;} public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;} public String getSiteId(){return siteId;} public void setSiteId(String v){siteId=v;} public String getPlantId(){return plantId;} public void setPlantId(String v){plantId=v;} public String getObjectType(){return objectType;} public void setObjectType(String v){objectType=v;} public String getObjectId(){return objectId;} public void setObjectId(String v){objectId=v;} public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;} public String getRequestedSkill(){return requestedSkill;} public void setRequestedSkill(String v){requestedSkill=v;} public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;} public String getParentTaskId(){return parentTaskId;} public void setParentTaskId(String v){parentTaskId=v;} public String getMatchedFlowId(){return matchedFlowId;} public void setMatchedFlowId(String v){matchedFlowId=v;} public String getMatchedRuleId(){return matchedRuleId;} public void setMatchedRuleId(String v){matchedRuleId=v;} public String getRoutingPath(){return routingPath;} public void setRoutingPath(String v){routingPath=v;} public String getRoutingPolicy(){return routingPolicy;} public void setRoutingPolicy(String v){routingPolicy=v;} public List<String> getRequiredCapabilities(){return requiredCapabilities;} public void setRequiredCapabilities(List<String> v){requiredCapabilities=v;} public String getCreatedReason(){return createdReason;} public void setCreatedReason(String v){createdReason=v;} public String getLifecycleReason(){return lifecycleReason;} public void setLifecycleReason(String v){lifecycleReason=v;} public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;} public OffsetDateTime getTerminalAt(){return terminalAt;} public void setTerminalAt(OffsetDateTime v){terminalAt=v;} public String getExternalExecutionKey(){return externalExecutionKey;} public void setExternalExecutionKey(String v){externalExecutionKey=v;}
}
JAVA
w com/opensocket/aievent/core/task/TaskRepository.java <<'JAVA'
package com.opensocket.aievent.core.task; import java.util.*; public interface TaskRepository { TaskRecord save(TaskRecord task); Optional<TaskRecord> findById(String taskId); }
JAVA
w com/opensocket/aievent/core/task/TaskOrchestrationFacade.java <<'JAVA'
package com.opensocket.aievent.core.task; import java.time.*; public interface TaskOrchestrationFacade { TaskRecord cancelTask(String taskId,String reason,OffsetDateTime now); }
JAVA
w com/opensocket/aievent/core/callback/TaskCallbackGuardContext.java <<'JAVA'
package com.opensocket.aievent.core.callback; import java.time.*; import java.util.*; public record TaskCallbackGuardContext(String tenantId,String taskId,String callbackId,TaskCallbackType callbackType,String dispatchRequestId,String assignmentId,String agentId,String idempotencyKey,Map<String,Object> payload,OffsetDateTime occurredAt) {}
JAVA
w com/opensocket/aievent/core/callback/TaskCallbackType.java <<'JAVA'
package com.opensocket.aievent.core.callback; public enum TaskCallbackType { ACK, PROGRESS, RESULT, ERROR }
JAVA
w com/opensocket/aievent/core/events/TaskCallbackAcceptedEvent.java <<'JAVA'
package com.opensocket.aievent.core.events; import java.time.*; import java.util.*; public record TaskCallbackAcceptedEvent(String eventId,String callbackId,String callbackType,String taskId,String tenantId,String dispatchRequestId,String assignmentId,String agentId,String taskStatus,String dispatchStatus,String idempotencyKey,String callbackFingerprint,String resultStatus,String errorCode,String errorMessage,String message,Integer progressPercent,Map<String,Object> payload,OffsetDateTime acceptedAt,OffsetDateTime occurredAt) {}
JAVA
w com/opensocket/aievent/core/routing/governance/action/EffectfulActionRuntimeProperties.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.action; public class EffectfulActionRuntimeProperties { public boolean isEnabled(){return true;} public boolean isCallbackProjectionEnabled(){return true;} public boolean isScheduledRecoveryEnabled(){return true;} public long getDefaultTimeoutSeconds(){return 900;} public long getDefaultExpirationSeconds(){return 3600;} public int getRecoveryBatchSize(){return 100;} public double getWarningTimeoutRate(){return .05;} public double getCriticalTimeoutRate(){return .15;} }
JAVA
w com/opensocket/aievent/core/assignment/AssignmentDecisionResult.java <<'JAVA'
package com.opensocket.aievent.core.assignment;
public record AssignmentDecisionResult(boolean assignmentCreated,String assignmentId,String selectedAgentId,String selectedGatewayNodeId,String selectedAgentSessionId,String selectedSiteId,String routingDecisionId,String assignmentStatus,String reason,boolean dispatchRequestCreated,String dispatchRequestId,String dispatchStatus,String dispatchReviewMode,String dispatchEligibilityStatus,String dispatchGatewayPath,String dispatchReason) {}
JAVA
w com/opensocket/aievent/core/assignment/TaskAssignmentService.java <<'JAVA'
package com.opensocket.aievent.core.assignment; import com.opensocket.aievent.core.task.TaskRecord;
public class TaskAssignmentService { public AssignmentDecisionResult assignToSpecificAgent(TaskRecord task,String agentId,String reason){ return new AssignmentDecisionResult(true,"assignment-"+task.getTaskId(),agentId,null,null,null,"routing-"+task.getTaskId(),"ASSIGNED",reason,true,"dispatch-"+task.getTaskId(),"REQUESTED",null,"ELIGIBLE",null,"Dispatched by P8 harness"); } }
JAVA

w com/opensocket/aievent/core/routing/governance/TaskRequirementEvidence.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance; import java.time.*; import java.util.*;
public class TaskRequirementEvidence { private String evidenceId,taskId,tenantId,sourceSystem; private Map<String,Object> evidence=new LinkedHashMap<>(); public void setTenantId(String v){tenantId=v;} public String getTenantId(){return tenantId;} public void setEvidenceId(String v){evidenceId=v;} public String getEvidenceId(){return evidenceId;} public void setTaskId(String v){taskId=v;} public String getTaskId(){return taskId;} public void setMatchedFlowId(String v){} public void setMatchedRuleId(String v){} public void setSourceSystem(String v){sourceSystem=v;} public void setResolutionMode(RequirementResolutionMode v){} public void setRequiredOperations(List<DispatchOperation> v){} public void setRequiredCapabilities(List<String> v){} public void setSideEffectLevel(SideEffectLevel v){} public void setCandidatePoolMode(CandidatePoolMode v){} public void setRoutingStrategy(GenericRoutingStrategy v){} public void setExplicitActionAuthorizationRequired(boolean v){} public void setDecisionStatus(RequirementDecisionStatus v){} public void setReasonCode(String v){} public void setResolverVersion(int v){} public void setEvidence(Map<String,Object> v){evidence=new LinkedHashMap<>(v);} public Map<String,Object> getEvidence(){return evidence;} public void setCreatedAt(OffsetDateTime v){} public void setCreatedBy(String v){} public void validate(){if(tenantId==null||taskId==null||sourceSystem==null)throw new IllegalArgumentException("invalid requirement evidence");} }
JAVA
w com/opensocket/aievent/core/routing/governance/TaskRequirementEvidenceRepository.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance; import java.util.*; public interface TaskRequirementEvidenceRepository { TaskRequirementEvidence append(TaskRequirementEvidence evidence); }
JAVA

w harness/P9EffectfulActionRuntimeHarness.java <<'JAVA'
package harness;
import java.time.*; import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.assignment.*;
import com.opensocket.aievent.core.events.TaskCallbackAcceptedEvent;
import com.opensocket.aievent.core.routing.governance.*;
import com.opensocket.aievent.core.routing.governance.action.*;
import com.opensocket.aievent.core.task.*;
public final class P9EffectfulActionRuntimeHarness {
 static final String TENANT="tenant-random"; static final String SOURCE="SOURCE_RANDOM_2001"; static final String ACTION="ACTION_RANDOM_CHANGE"; static final String COMPENSATE="ACTION_RANDOM_REVERSE"; static final String AGENT="agent-random";
 public static void main(String[] args){
  Repo repo=new Repo(); Tasks tasks=new Tasks(); Evidence requirements=new Evidence();
  ActionGovernanceService service=new ActionGovernanceService(repo,tasks,requirements,new TaskAssignmentService(),new ObjectMapper());
  saveAction(service,ACTION,true,COMPENSATE); saveAction(service,COMPENSATE,false,null);
  AgentActionGrant primary=grant(service,"grant-primary",ACTION,"grant-requester","grant-approver");
  grant(service,"grant-compensation",COMPENSATE,"comp-requester","comp-approver");

  TaskRecord analysis=analysisTask("analysis-success"); tasks.save(analysis);
  ProposedAction proposal=approvedProposal(service,"proposal-success",analysis.getTaskId(),"object-success",ACTION,"analyst-success","approval-success");
  ActionTaskMaterializationResult materialized=service.materializeAndDispatch(TENANT,proposal.getProposalId(),"operator-success");
  EffectfulActionTaskLink link=repo.findTaskLinkByTask(TENANT,materialized.actionTaskId()).orElseThrow();
  require(link.getExternalExecutionKey()!=null&&!link.getExternalExecutionKey().isBlank(),"external execution key persisted");
  require(link.getExternalExecutionKey().equals(tasks.findById(materialized.actionTaskId()).orElseThrow().getExternalExecutionKey()),"execution key copied to Task dispatch contract");

  service.handleAcceptedCallback(callback("cb-ack", "ACK", link, Map.of("externalExecutionKey",link.getExternalExecutionKey()), null, null));
  require(repo.linkByTask(link.getActionTaskId()).getStatus()==EffectfulActionHandoffStatus.ACKNOWLEDGED,"ACK projection");
  service.handleAcceptedCallback(callback("cb-progress", "PROGRESS", link, Map.of("externalExecutionKey",link.getExternalExecutionKey()), null, null));
  require(repo.linkByTask(link.getActionTaskId()).getStatus()==EffectfulActionHandoffStatus.RUNNING,"PROGRESS projection");
  service.handleAcceptedCallback(callback("cb-result", "RESULT", link, Map.of("externalExecutionKey",link.getExternalExecutionKey(),"result","ok"), "SUCCEEDED", null));
  require(repo.linkByTask(link.getActionTaskId()).getStatus()==EffectfulActionHandoffStatus.SUCCEEDED,"RESULT projection");
  int receipts=repo.receiptsByCallback.size();
  service.handleAcceptedCallback(callback("cb-result", "RESULT", link, Map.of("externalExecutionKey",link.getExternalExecutionKey(),"result","ok"), "SUCCEEDED", null));
  require(repo.receiptsByCallback.size()==receipts,"duplicate Module Event is idempotent");

  EffectfulActionTaskLink mismatch=materialize(service,tasks,"analysis-mismatch","proposal-mismatch","object-mismatch",ACTION);
  service.handleAcceptedCallback(callback("cb-mismatch", "ACK", mismatch, Map.of("externalExecutionKey","wrong-key"), null, null));
  require(repo.linkByTask(mismatch.getActionTaskId()).getStatus()==EffectfulActionHandoffStatus.DISPATCHED,"mismatched execution key cannot advance lifecycle");
  require(repo.manual.values().stream().anyMatch(c->ActionRuntimeReasonCode.EXTERNAL_EXECUTION_KEY_MISMATCH.name().equals(c.getReasonCode())),"execution key mismatch opens manual case");

  EffectfulActionTaskLink cancellable=materialize(service,tasks,"analysis-cancel","proposal-cancel","object-cancel",ACTION);
  service.requestCancellation(TENANT,cancellable.getActionTaskId(),"operator-cancel","No longer required");
  require(repo.linkByTask(cancellable.getActionTaskId()).getStatus()==EffectfulActionHandoffStatus.CANCELLED,"pre-execution cancellation");
  require(tasks.findById(cancellable.getActionTaskId()).orElseThrow().getStatus()==TaskStatus.CANCELLED,"Task lifecycle cancelled");

  EffectfulActionTaskLink timeout=materialize(service,tasks,"analysis-timeout","proposal-timeout","object-timeout",ACTION);
  timeout.setTimeoutAt(OffsetDateTime.now().minusSeconds(1)); repo.saveTaskLink(timeout);
  ActionRuntimeRecoveryResult recovery=service.processRuntimeDeadlines(OffsetDateTime.now(),100);
  require(recovery.timedOut()>=1,"deadline recovery timed out action");
  require(repo.linkByTask(timeout.getActionTaskId()).getStatus()==EffectfulActionHandoffStatus.TIMED_OUT,"timeout status");

  EffectfulActionTaskLink revoked=materialize(service,tasks,"analysis-revoke","proposal-revoke","object-revoke",ACTION);
  service.revokeGrant(TENANT,primary.getGrantId(),"security-admin");
  require(repo.linkByTask(revoked.getActionTaskId()).getStatus()==EffectfulActionHandoffStatus.GRANT_REVOKED,"Grant revocation invalidates unstarted action");

  primary=grant(service,"grant-primary-2",ACTION,"grant-requester-2","grant-approver-2");
  EffectfulActionTaskLink failed=materialize(service,tasks,"analysis-failure","proposal-failure","object-failure",ACTION);
  service.handleAcceptedCallback(callback("cb-start-failure", "PROGRESS", failed, Map.of("externalExecutionKey",failed.getExternalExecutionKey()), null, null));
  service.handleAcceptedCallback(callback("cb-error", "ERROR", failed, Map.of("externalExecutionKey",failed.getExternalExecutionKey()), "FAILED", "REMOTE_FAILURE"));
  EffectfulActionTaskLink compensationPending=repo.linkByTask(failed.getActionTaskId());
  require(compensationPending.getStatus()==EffectfulActionHandoffStatus.FAILED,"failed execution status remains authoritative while compensation awaits approval");
  require(compensationPending.getCompensationProposalId()!=null,"failed reversible action prepares compensation");
  ProposedAction compensation=repo.findCompensationProposal(TENANT,repo.findProposalById(TENANT,failed.getProposalId()).orElseThrow().getProposalId()).orElseThrow();
  require(ProposedActionKind.COMPENSATION.name().equals(compensation.getProposalKind()),"compensation is separate proposal");
  ActionApprovalRequest compensationRequest=service.submitProposal(TENANT,compensation.getProposalId(),"compensation-requester",null);
  service.decideApproval(TENANT,compensationRequest.getRequestId(),ActionApprovalDecisionType.APPROVE,"compensation-approver","independent compensation approval");
  ActionTaskMaterializationResult compensationTask=service.materializeAndDispatch(TENANT,compensation.getProposalId(),"compensation-operator");
  EffectfulActionTaskLink compensationLink=repo.findTaskLinkByTask(TENANT,compensationTask.actionTaskId()).orElseThrow();
  service.handleAcceptedCallback(callback("cb-comp-result", "RESULT", compensationLink, Map.of("externalExecutionKey",compensationLink.getExternalExecutionKey()), "SUCCEEDED", null));
  require(repo.linkByTask(failed.getActionTaskId()).getStatus()==EffectfulActionHandoffStatus.COMPENSATED,"successful compensation closes parent lifecycle");

  EffectfulActionRuntimeMetrics metrics=service.runtimeMetrics(TENANT);
  require(metrics.total()>0&&metrics.openManualCases()>0,"runtime metrics include tasks and manual cases");
  require(repo.evidence.stream().anyMatch(e->e.getEvidenceType()==ActionEvidenceType.CALLBACK_ACCEPTED),"callback evidence appended");
  require(repo.evidence.stream().anyMatch(e->e.getEvidenceType()==ActionEvidenceType.COMPENSATION_RESULT),"compensation evidence appended");
  System.out.println("P9 effectful Action runtime integration harness passed");
 }
 static void saveAction(ActionGovernanceService service,String code,boolean reversible,String compensation){ActionCatalogEntry a=new ActionCatalogEntry();a.setActionName(code);a.setOperation(DispatchOperation.REMEDIATE);a.setSideEffectLevel(SideEffectLevel.REVERSIBLE_WRITE);a.setRiskLevel(ActionRiskLevel.HIGH);a.setRequiredCapabilityCode("CAP_RANDOM_ACTION");a.setRequiredApprovalCount(1);a.setReversible(reversible);a.setCompensationActionCode(compensation);a.setStatus("ACTIVE");service.saveAction(TENANT,code,a,"catalog-admin");}
 static AgentActionGrant grant(ActionGovernanceService service,String id,String action,String requester,String approver){AgentActionGrant g=new AgentActionGrant();g.setAgentId(AGENT);g.setSourceSystem(SOURCE);g.setActionCode(action);g=service.saveGrant(TENANT,id,g,requester);return service.approveGrant(TENANT,g.getGrantId(),approver);}
 static ProposedAction approvedProposal(ActionGovernanceService service,String id,String analysis,String object,String action,String proposer,String approver){ProposedAction p=new ProposedAction();p.setProposalId(id);p.setAnalysisTaskId(analysis);p.setSourceSystem(SOURCE);p.setActionCode(action);p.setTargetAgentId(AGENT);p.setTargetFlowId("flow-random");p.setTargetRuleId("rule-random");p.setTargetObjectType("OBJECT_RANDOM");p.setTargetObjectId(object);p.setRationale("Apply an independently approved corrective action");p.setParameters(Map.of("object",object));p.setIdempotencyKey("idem-"+id);p=service.createProposal(TENANT,p,proposer);ActionApprovalRequest r=service.submitProposal(TENANT,p.getProposalId(),proposer+"-requester",null);service.decideApproval(TENANT,r.getRequestId(),ActionApprovalDecisionType.APPROVE,approver,"approved");return service.searchProposals(TENANT,analysis,null,100).stream().filter(x->x.getProposalId().equals(id)).findFirst().orElseThrow();}
 static EffectfulActionTaskLink materialize(ActionGovernanceService service,Tasks tasks,String analysisId,String proposalId,String object,String action){tasks.save(analysisTask(analysisId));ProposedAction p=approvedProposal(service,proposalId,analysisId,object,action,"analyst-"+proposalId,"approver-"+proposalId);ActionTaskMaterializationResult result=service.materializeAndDispatch(TENANT,p.getProposalId(),"operator-"+proposalId);return ((RepoHolder)serviceRepository(service)).repo.findTaskLinkByTask(TENANT,result.actionTaskId()).orElseThrow();}
 static Object serviceRepository(ActionGovernanceService service){try{var f=ActionGovernanceService.class.getDeclaredField("repository");f.setAccessible(true);return new RepoHolder((Repo)f.get(service));}catch(Exception ex){throw new IllegalStateException(ex);}}
 record RepoHolder(Repo repo){}
 static TaskCallbackAcceptedEvent callback(String callbackId,String type,EffectfulActionTaskLink link,Map<String,Object> payload,String result,String error){return new TaskCallbackAcceptedEvent("event-"+callbackId,callbackId,type,link.getActionTaskId(),TENANT,"dispatch-"+link.getActionTaskId(),link.getAssignmentId(),AGENT,null,null,"idem-callback-"+callbackId,"fingerprint-"+callbackId,result,error,error,null,null,payload,OffsetDateTime.now(),OffsetDateTime.now());}
 static TaskRecord analysisTask(String id){TaskRecord t=new TaskRecord();t.setTaskId(id);t.setTenantId(TENANT);t.setSourceSystem(SOURCE);t.setStatus(TaskStatus.SUCCEEDED);t.setTaskType(TaskType.INCIDENT_RESPONSE);t.setPriority(TaskPriority.HIGH);t.setIncidentId("incident-"+id);t.setObjectType("OBJECT_RANDOM");t.setObjectId("object-"+id);return t;}
 static void require(boolean ok,String label){if(!ok)throw new IllegalStateException(label);}
 static final class Tasks implements TaskRepository { final Map<String,TaskRecord> map=new LinkedHashMap<>(); public TaskRecord save(TaskRecord t){map.put(t.getTaskId(),t);return t;} public Optional<TaskRecord> findById(String id){return Optional.ofNullable(map.get(id));} }
 static final class Evidence implements TaskRequirementEvidenceRepository { final List<TaskRequirementEvidence> items=new ArrayList<>(); public TaskRequirementEvidence append(TaskRequirementEvidence e){items.add(e);return e;} }
 static final class Repo implements ActionGovernanceRepository {
  final Map<String,ActionCatalogEntry> actions=new LinkedHashMap<>(); final Map<String,AgentActionGrant> grants=new LinkedHashMap<>(); final Map<String,ProposedAction> proposals=new LinkedHashMap<>(); final Map<String,ActionApprovalRequest> requests=new LinkedHashMap<>(); final List<ActionApprovalDecision> decisions=new ArrayList<>(); final Map<String,EffectfulActionTaskLink> links=new LinkedHashMap<>(); final List<EffectfulActionEvidence> evidence=new ArrayList<>(); final Map<String,EffectfulActionCallbackReceipt> receiptsByCallback=new LinkedHashMap<>(); final Map<String,EffectfulActionCallbackReceipt> receiptsByIdempotency=new LinkedHashMap<>(); final Map<String,EffectfulActionManualCase> manual=new LinkedHashMap<>();
  public ActionCatalogEntry saveAction(ActionCatalogEntry a){actions.put(a.getActionCode(),a);return a;} public Optional<ActionCatalogEntry> findActionByCode(String t,String c){return Optional.ofNullable(actions.get(c));} public List<ActionCatalogEntry> listActions(String t,String s,int l){return actions.values().stream().limit(l).toList();}
  public AgentActionGrant saveGrant(AgentActionGrant g){grants.put(g.getGrantId(),g);return g;} public Optional<AgentActionGrant> findGrantById(String t,String id){return Optional.ofNullable(grants.get(id));} public Optional<AgentActionGrant> findActiveGrant(String t,String a,String s,String c,OffsetDateTime at){return grants.values().stream().filter(g->g.getAgentId().equals(a)&&g.getSourceSystem().equals(s)&&g.getActionCode().equals(c)&&g.isActiveAt(at)).findFirst();} public Optional<AgentActionGrant> findActiveGrantForUpdate(String t,String a,String s,String c,OffsetDateTime at){return findActiveGrant(t,a,s,c,at);} public List<AgentActionGrant> searchGrants(String t,String a,String s,String c,String st,int l){return grants.values().stream().limit(l).toList();}
  public ProposedAction saveProposal(ProposedAction p){proposals.put(p.getProposalId(),p);return p;} public Optional<ProposedAction> findProposalById(String t,String id){return Optional.ofNullable(proposals.get(id));} public Optional<ProposedAction> findProposalByIdForUpdate(String t,String id){return findProposalById(t,id);} public Optional<ProposedAction> findProposalByIdempotencyKey(String t,String k){return proposals.values().stream().filter(p->k.equals(p.getIdempotencyKey())).findFirst();} public List<ProposedAction> searchProposals(String t,String a,String s,int l){return proposals.values().stream().filter(p->a==null||a.equals(p.getAnalysisTaskId())).limit(l).toList();}
  public ActionApprovalRequest saveApprovalRequest(ActionApprovalRequest r){requests.put(r.getRequestId(),r);return r;} public Optional<ActionApprovalRequest> findApprovalRequestById(String t,String id){return Optional.ofNullable(requests.get(id));} public Optional<ActionApprovalRequest> findApprovalRequestByIdForUpdate(String t,String id){return findApprovalRequestById(t,id);} public Optional<ActionApprovalRequest> findApprovalRequestByProposal(String t,String p){return requests.values().stream().filter(r->r.getProposalId().equals(p)).findFirst();} public Optional<ActionApprovalRequest> findApprovalRequestByProposalForUpdate(String t,String p){return findApprovalRequestByProposal(t,p);} public List<ActionApprovalRequest> searchApprovalRequests(String t,String s,int l){return requests.values().stream().limit(l).toList();} public ActionApprovalDecision appendApprovalDecision(ActionApprovalDecision d){decisions.add(d);return d;} public List<ActionApprovalDecision> listApprovalDecisions(String t,String r){return decisions.stream().filter(d->d.getRequestId().equals(r)).toList();}
  public EffectfulActionTaskLink saveTaskLink(EffectfulActionTaskLink l){links.put(l.getProposalId(),l);return l;} public Optional<EffectfulActionTaskLink> findTaskLinkByProposal(String t,String p){return Optional.ofNullable(links.get(p));} public Optional<EffectfulActionTaskLink> findTaskLinkByTask(String t,String task){return links.values().stream().filter(l->l.getActionTaskId().equals(task)).findFirst();} public Optional<EffectfulActionTaskLink> findTaskLinkByTaskForUpdate(String t,String task){return findTaskLinkByTask(t,task);} EffectfulActionTaskLink linkByTask(String task){return findTaskLinkByTask(TENANT,task).orElseThrow();}
  public List<EffectfulActionTaskLink> findNonTerminalLinksByGrant(String t,String g,int l){return links.values().stream().filter(x->g.equals(x.getActionGrantId())&&!x.isTerminal()).limit(l).toList();} public List<EffectfulActionTaskLink> findRuntimeDeadlineCandidates(OffsetDateTime at,int l){return links.values().stream().filter(x->!x.isTerminal()&&((x.getTimeoutAt()!=null&&!x.getTimeoutAt().isAfter(at))||(x.getExpiresAt()!=null&&!x.getExpiresAt().isAfter(at)))).limit(l).toList();} public List<EffectfulActionTaskLink> searchTaskLinks(String t,String s,int l){return links.values().stream().filter(x->s==null||x.getStatus().name().equals(s)).limit(l).toList();} public Map<String,Long> countTaskLinksByStatus(String t){Map<String,Long> m=new LinkedHashMap<>();links.values().forEach(x->m.merge(x.getStatus().name(),1L,Long::sum));return m;}
  public boolean tryReserveCallbackReceipt(EffectfulActionCallbackReceipt r){if(receiptsByCallback.containsKey(r.getCallbackId())||receiptsByIdempotency.containsKey(r.getIdempotencyKey()))return false;receiptsByCallback.put(r.getCallbackId(),r);receiptsByIdempotency.put(r.getIdempotencyKey(),r);return true;} public Optional<EffectfulActionCallbackReceipt> findCallbackReceipt(String t,String c){return Optional.ofNullable(receiptsByCallback.get(c));} public Optional<EffectfulActionCallbackReceipt> findCallbackReceiptByIdempotencyKey(String t,String k){return Optional.ofNullable(receiptsByIdempotency.get(k));} public long countReplayRejected(String t){return evidence.stream().filter(e->e.getEvidenceType()==ActionEvidenceType.CALLBACK_REPLAY_REJECTED).count();}
  public EffectfulActionManualCase saveManualCase(EffectfulActionManualCase c){manual.put(c.getCaseId(),c);return c;} public Optional<EffectfulActionManualCase> findManualCaseById(String t,String c){return Optional.ofNullable(manual.get(c));} public List<EffectfulActionManualCase> searchManualCases(String t,String s,int l){return manual.values().stream().filter(c->s==null||c.getStatus().name().equals(s)).limit(l).toList();} public long countManualCases(String t,String s){return manual.values().stream().filter(c->s==null||c.getStatus().name().equals(s)).count();} public Optional<ProposedAction> findCompensationProposal(String t,String parent){return proposals.values().stream().filter(p->parent.equals(p.getParentProposalId())&&ProposedActionKind.COMPENSATION.name().equals(p.getProposalKind())).findFirst();}
  public EffectfulActionEvidence appendEvidence(EffectfulActionEvidence e){if(evidence.stream().noneMatch(x->x.getIdempotencyKey().equals(e.getIdempotencyKey())))evidence.add(e);return e;} public List<EffectfulActionEvidence> listEvidence(String t,String p,int l){return evidence.stream().filter(e->e.getProposalId().equals(p)).limit(l).toList();}
 }
}
JAVA

GOV="$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance"
ACTION="$GOV/action"
ACTUAL=(
 "$GOV/DispatchOperation.java" "$GOV/SideEffectLevel.java" "$GOV/DispatchApprovalStatus.java" "$GOV/DispatchGovernanceStatus.java"
 "$GOV/CandidatePoolMode.java" "$GOV/GenericRoutingStrategy.java" "$GOV/RequirementDecisionStatus.java" "$GOV/RequirementResolutionMode.java"
)
while IFS= read -r f; do ACTUAL+=("$f"); done < <(find "$ACTION" -maxdepth 1 -name '*.java' | sort)
ACTUAL+=(
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionGovernanceRepository.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionGovernanceService.java"
)
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
"$JAVAC" -d "$OUT" "${STUBS[@]}" "${ACTUAL[@]}"
"$JAVA" -cp "$OUT" harness.P9EffectfulActionRuntimeHarness
