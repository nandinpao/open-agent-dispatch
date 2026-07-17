package com.opensocket.aievent.database.persistence.task.converter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.database.persistence.task.po.TaskAssignmentPo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix="assignment", name="store", havingValue="MYBATIS")
public class TaskAssignmentPersistenceConverter {


    public TaskAssignmentPo toPo(TaskAssignment a){TaskAssignmentPo r=new TaskAssignmentPo();r.setAssignmentId(a.getAssignmentId());r.setTaskId(a.getTaskId());r.setIncidentId(a.getIncidentId());r.setAgentId(a.getAgentId());r.setAgentType(a.getAgentType());r.setOwnerGatewayNodeId(a.getOwnerGatewayNodeId());r.setAgentSessionId(a.getAgentSessionId());r.setSiteId(a.getSiteId());r.setEventStage(a.getEventStage());r.setOriginSourceSystem(a.getOriginSourceSystem());r.setTargetSystem(a.getTargetSystem());r.setRequestedSkill(a.getRequestedSkill());r.setCorrelationId(a.getCorrelationId());r.setParentTaskId(a.getParentTaskId());r.setHandoffMode(a.getHandoffMode());r.setMatchedFlowId(a.getMatchedFlowId());r.setMatchedRuleId(a.getMatchedRuleId());r.setAssignedPoolId(a.getAssignedPoolId());r.setTargetPoolId(a.getTargetPoolId());r.setRoutingPath(a.getRoutingPath());r.setStatus(a.getStatus()==null?null:a.getStatus().name());r.setRoutingPolicy(a.getRoutingPolicy());r.setRoutingDecisionId(a.getRoutingDecisionId());r.setDispatchAttemptId(a.getDispatchAttemptId());r.setLeaseId(a.getLeaseId());r.setFencingToken(a.getFencingToken());r.setLeaseExpiresAt(a.getLeaseExpiresAt());r.setScore(a.getScore());r.setReason(a.getReason());r.setCapacityReserved(a.isCapacityReserved());r.setCapacityReservedAt(a.getCapacityReservedAt());r.setCapacityReleasedAt(a.getCapacityReleasedAt());r.setCreatedAt(a.getCreatedAt());r.setUpdatedAt(a.getUpdatedAt());return r;}

    public TaskAssignment toDomain(TaskAssignmentPo r){TaskAssignment a=new TaskAssignment();a.setAssignmentId(r.getAssignmentId());a.setTaskId(r.getTaskId());a.setIncidentId(r.getIncidentId());a.setAgentId(r.getAgentId());a.setAgentType(r.getAgentType());a.setOwnerGatewayNodeId(r.getOwnerGatewayNodeId());a.setAgentSessionId(r.getAgentSessionId());a.setSiteId(r.getSiteId());a.setEventStage(r.getEventStage());a.setOriginSourceSystem(r.getOriginSourceSystem());a.setTargetSystem(r.getTargetSystem());a.setRequestedSkill(r.getRequestedSkill());a.setCorrelationId(r.getCorrelationId());a.setParentTaskId(r.getParentTaskId());a.setHandoffMode(r.getHandoffMode());a.setMatchedFlowId(r.getMatchedFlowId());a.setMatchedRuleId(r.getMatchedRuleId());a.setAssignedPoolId(r.getAssignedPoolId());a.setTargetPoolId(r.getTargetPoolId());a.setRoutingPath(r.getRoutingPath());a.setStatus(r.getStatus()==null?null:AssignmentStatus.valueOf(r.getStatus()));a.setRoutingPolicy(r.getRoutingPolicy());a.setRoutingDecisionId(r.getRoutingDecisionId());a.setDispatchAttemptId(r.getDispatchAttemptId());a.setLeaseId(r.getLeaseId());a.setFencingToken(r.getFencingToken());a.setLeaseExpiresAt(r.getLeaseExpiresAt());a.setScore(r.getScore());a.setReason(r.getReason());a.setCapacityReserved(r.isCapacityReserved());a.setCapacityReservedAt(r.getCapacityReservedAt());a.setCapacityReleasedAt(r.getCapacityReleasedAt());a.setCreatedAt(r.getCreatedAt());a.setUpdatedAt(r.getUpdatedAt());return a;}
}
