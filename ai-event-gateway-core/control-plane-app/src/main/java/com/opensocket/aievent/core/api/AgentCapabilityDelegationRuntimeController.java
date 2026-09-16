package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.CapabilityDelegationReceipt;
import com.opensocket.aievent.core.capability.CapabilityDelegationRequest;
import com.opensocket.aievent.core.capability.CapabilityDelegationLiveClosureEvidenceService;
import com.opensocket.aievent.core.capability.ManagedCapabilityDelegationRuntimeService;
import com.opensocket.aievent.core.resourceaccess.runtime.AgentTaskRuntimeAuthorizationService;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stage 6 canonical Agent-originated Capability Delegation API. Source Agent identity is derived
 * from the authenticated Gateway runtime session. Request body contains WHAT only.
 * Resource Access remains a default-off feature; when enabled, the optional runtime authorization
 * bridge performs the canonical task.execute decision before delegation is accepted.
 */
@RestController
@RequestMapping("/internal/control-plane/tasks")
public class AgentCapabilityDelegationRuntimeController {
    private static final Logger log = LoggerFactory.getLogger(AgentCapabilityDelegationRuntimeController.class);
    private final ManagedCapabilityDelegationRuntimeService delegation;
    private final CapabilityDelegationLiveClosureEvidenceService closureEvidence;
    private final ObjectProvider<AgentTaskRuntimeAuthorizationService> runtimeAuthorizationProvider;
    private final TaskOperationalQuery tasks;

    public AgentCapabilityDelegationRuntimeController(ManagedCapabilityDelegationRuntimeService delegation,
            CapabilityDelegationLiveClosureEvidenceService closureEvidence,
            ObjectProvider<AgentTaskRuntimeAuthorizationService> runtimeAuthorizationProvider, TaskOperationalQuery tasks) {
        this.delegation=delegation;
        this.closureEvidence=closureEvidence;
        this.runtimeAuthorizationProvider=runtimeAuthorizationProvider;
        this.tasks=tasks;
    }

    @PostMapping("/{taskId}/capability-delegations")
    public CapabilityDelegationReceipt delegate(@PathVariable String taskId,
            @RequestHeader("X-Agent-Id") String agentId,
            @RequestHeader(value="X-Agent-Session-Id",required=false) String agentSessionId,
            @RequestHeader(value="X-Gateway-Node-Id",required=false) String gatewayNodeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value="X-Correlation-Id",required=false) String correlationId,
            @RequestBody CapabilityDelegationRequest body) {
        String sourceAgentId=required(agentId,"X-Agent-Id");
        TaskRecord task=tasks.findTask(taskId).orElseThrow(()->new IllegalArgumentException("Task not found: "+taskId));
        String tenantId=required(task.getTenantId(),"task.tenantId");
        String cid=correlationId==null||correlationId.isBlank()?UUID.randomUUID().toString():correlationId.trim();
        log.info("capability_delegation_journey stage=CORE_CANONICAL_REQUEST_RECEIVED correlationId={} tenantId={} taskId={} agentId={} agentSessionId={} gatewayNodeId={} targetTopologyAccepted=false",
                cid, tenantId, taskId, sourceAgentId, safe(agentSessionId), safe(gatewayNodeId));
        AgentTaskRuntimeAuthorizationService runtimeAuthorization=runtimeAuthorizationProvider.getIfAvailable();
        if(runtimeAuthorization!=null){
            runtimeAuthorization.authorizeExecution(taskId,sourceAgentId,cid,"CAPABILITY_DELEGATION_STAGE6");
        }
        CapabilityDelegationReceipt receipt=delegation.delegate(tenantId,taskId,sourceAgentId,agentSessionId,gatewayNodeId,idempotencyKey,cid,body);
        log.info("capability_delegation_journey stage=CORE_CANONICAL_REQUEST_PERSISTED correlationId={} tenantId={} taskId={} agentId={} delegationId={} delegationStatus={} childTaskId={} assignmentId={} dispatchRequestId={} reasonCodes={}",
                cid, tenantId, taskId, sourceAgentId, receipt.delegationId(), receipt.status(), safe(receipt.childTaskId()),
                safe(receipt.assignmentId()), safe(receipt.dispatchRequestId()), receipt.reasonCodes());
        return receipt;
    }

    /** Read-only Gateway/TestLab evidence endpoint. It cannot create, route, cancel or complete a delegation. */
    @GetMapping("/{taskId}/capability-delegations/{delegationId}/closure-evidence")
    public CapabilityDelegationLiveClosureEvidenceService.ClosureEvidence closureEvidence(
            @PathVariable String taskId, @PathVariable String delegationId) {
        TaskRecord task=tasks.findTask(taskId).orElseThrow(()->new IllegalArgumentException("Task not found: "+taskId));
        return closureEvidence.find(required(task.getTenantId(),"task.tenantId"), taskId, delegationId);
    }

    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static String safe(String value){return value==null?"":value.trim();}
}
