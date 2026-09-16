package com.opensocket.aievent.database.persistence.execution.repository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.core.dispatch.*;
import com.opensocket.aievent.database.persistence.execution.dao.DispatchAssignmentEvidenceDao;
import com.opensocket.aievent.database.persistence.execution.po.DispatchAssignmentEvidencePo;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="dispatch",name="request-store",havingValue="MYBATIS")
public class MybatisDispatchAssignmentEvidenceRepository implements DispatchAssignmentEvidenceRepository {
 private final DispatchAssignmentEvidenceDao dao; public MybatisDispatchAssignmentEvidenceRepository(DispatchAssignmentEvidenceDao dao){this.dao=dao;}
 public DispatchAssignmentEvidence append(DispatchAssignmentEvidence e){
  int rows=dao.insert(toPo(e));
  if(rows>0)return e;
  DispatchAssignmentEvidencePo existing=dao.findEvent(e.getDispatchRequestId(),e.getAttemptNo(),e.getEventType());
  return existing==null?e:toDomain(existing);
 }
 public List<DispatchAssignmentEvidence> findByDispatchRequest(String id,int limit){return dao.findByDispatchRequest(id,Math.max(1,Math.min(limit,1000))).stream().map(this::toDomain).toList();}
 public boolean existsEvent(String id,int attempt,String type){return dao.countEvent(id,attempt,type)>0;} public String mode(){return "MYBATIS";}
 private DispatchAssignmentEvidencePo toPo(DispatchAssignmentEvidence e){DispatchAssignmentEvidencePo p=new DispatchAssignmentEvidencePo();p.setEvidenceId(e.getEvidenceId());p.setTenantId(e.getTenantId());p.setDispatchRequestId(e.getDispatchRequestId());p.setAssignmentId(e.getAssignmentId());p.setTaskId(e.getTaskId());p.setAgentId(e.getAgentId());p.setAttemptNo(e.getAttemptNo());p.setEventType(e.getEventType());p.setOutboxStatus(e.getOutboxStatus());p.setWorkerId(e.getWorkerId());p.setClaimTokenHash(e.getClaimTokenHash());p.setClaimUntil(e.getClaimUntil());p.setDispatchTokenHash(e.getDispatchTokenHash());p.setFencingTokenHash(e.getFencingTokenHash());p.setRuntimeSessionId(e.getRuntimeSessionId());p.setAckEvidenceId(e.getAckEvidenceId());p.setGatewayStatus(e.getGatewayStatus());p.setRecoveryClassification(e.getRecoveryClassification());p.setEvidenceJson(e.getEvidenceJson());p.setOccurredAt(e.getOccurredAt());p.setCreatedAt(e.getCreatedAt());return p;}
 private DispatchAssignmentEvidence toDomain(DispatchAssignmentEvidencePo p){DispatchAssignmentEvidence e=new DispatchAssignmentEvidence();e.setEvidenceId(p.getEvidenceId());e.setTenantId(p.getTenantId());e.setDispatchRequestId(p.getDispatchRequestId());e.setAssignmentId(p.getAssignmentId());e.setTaskId(p.getTaskId());e.setAgentId(p.getAgentId());e.setAttemptNo(p.getAttemptNo());e.setEventType(p.getEventType());e.setOutboxStatus(p.getOutboxStatus());e.setWorkerId(p.getWorkerId());e.setClaimTokenHash(p.getClaimTokenHash());e.setClaimUntil(p.getClaimUntil());e.setDispatchTokenHash(p.getDispatchTokenHash());e.setFencingTokenHash(p.getFencingTokenHash());e.setRuntimeSessionId(p.getRuntimeSessionId());e.setAckEvidenceId(p.getAckEvidenceId());e.setGatewayStatus(p.getGatewayStatus());e.setRecoveryClassification(p.getRecoveryClassification());e.setEvidenceJson(p.getEvidenceJson());e.setOccurredAt(p.getOccurredAt());e.setCreatedAt(p.getCreatedAt());return e;}
}
