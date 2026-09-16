package com.opensocket.aievent.core.dispatch;

import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.OffsetDateTime; import java.time.ZoneOffset; import java.util.HexFormat; import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DispatchAssignmentEvidenceService {
 private final DispatchAssignmentEvidenceRepository repository;
 public DispatchAssignmentEvidenceService(DispatchAssignmentEvidenceRepository repository){this.repository=repository;}
 public DispatchAssignmentEvidence record(DispatchRequest request,String eventType,String gatewayStatus,String ackEvidenceId,String evidenceJson){
  if(request==null||request.getDispatchRequestId()==null)return null;
  if(repository.existsEvent(request.getDispatchRequestId(),request.getAttemptCount(),eventType))return repository.findByDispatchRequest(request.getDispatchRequestId(),50).stream().filter(e->eventType.equals(e.getEventType())&&e.getAttemptNo()==request.getAttemptCount()).findFirst().orElse(null);
  OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC); DispatchAssignmentEvidence e=new DispatchAssignmentEvidence();
  e.setEvidenceId("dae-"+UUID.randomUUID());e.setTenantId(request.getTenantId());e.setDispatchRequestId(request.getDispatchRequestId());e.setAssignmentId(request.getAssignmentId());e.setTaskId(request.getTaskId());e.setAgentId(request.getAgentId());e.setAttemptNo(request.getAttemptCount());e.setEventType(eventType);e.setOutboxStatus(request.getOutboxStatus()==null?null:request.getOutboxStatus().name());e.setWorkerId(request.getClaimedBy());e.setClaimTokenHash(hash(request.getClaimToken()));e.setClaimUntil(request.getClaimUntil());e.setDispatchTokenHash(first(request.getDispatchTokenHash(),hash(request.getDispatchToken())));e.setFencingTokenHash(first(request.getFencingTokenHash(),hash(request.getCommand()==null?null:request.getCommand().getFencingToken())));e.setRuntimeSessionId(first(request.getRuntimeSessionId(),request.getAgentSessionId()));e.setAckEvidenceId(ackEvidenceId);e.setGatewayStatus(gatewayStatus);e.setRecoveryClassification(request.getRecoveryClassification()==null?null:request.getRecoveryClassification().name());e.setEvidenceJson(evidenceJson==null?"{}":evidenceJson);e.setOccurredAt(now);e.setCreatedAt(now);return repository.append(e);
 }
 public boolean hasGatewayAccepted(String dispatchRequestId,int attemptNo){return repository.existsEvent(dispatchRequestId, attemptNo, "GATEWAY_ACCEPTED")
   || repository.existsEvent(dispatchRequestId, attemptNo, "GATEWAY_ACCEPTED_UNCONFIRMED");}
 private String first(String a,String b){return a!=null&&!a.isBlank()?a:b;}
 public static String hash(String value){if(value==null||value.isBlank())return null;try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable",e);}}
}
