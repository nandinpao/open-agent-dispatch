package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.assignment.TaskAuthorizationProjectionPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RS4 Agent runtime -> Task Resource Access bridge. Phase 10 uses the canonical AGENT principal
 * type while IAM remains the single machine identity authority.
 */
@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class AgentTaskRuntimeAuthorizationService {
    private static final Logger log=LoggerFactory.getLogger(AgentTaskRuntimeAuthorizationService.class);
    private final ResourceAuthorizationPort authorization;
    private final TaskRepository tasks;
    @Autowired(required=false) private TaskAuthorizationProjectionPort taskAuthorizationProjectionPort;
    @Value("${resource-access.agent-runtime-enforce:false}") private boolean enforce;

    public AgentTaskRuntimeAuthorizationService(ResourceAuthorizationPort authorization,TaskRepository tasks){
        this.authorization=Objects.requireNonNull(authorization);this.tasks=Objects.requireNonNull(tasks);
    }

    public boolean isEnforced(){ return enforce; }

    public AuthorizationDecision authorizeExecution(String taskId,String agentId,String correlationId,String purpose){
        if(agentId==null||agentId.isBlank()) throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Agent identity is required for Task runtime operation.");
        TaskRecord task=tasks.findById(taskId).orElseThrow(()->new StandardApiException(StandardApiErrorCode.NOT_FOUND,"Task not found: "+taskId));
        // Refresh the one-way participant projection before the decision so historical V148 backfill and
        // current assignment changes use the same canonical Task participant authority.
        if(taskAuthorizationProjectionPort!=null) taskAuthorizationProjectionPort.projectTask(task.getTenantId(),task.getTaskId(),correlationId);
        Instant now=Instant.now();
        PrincipalRef principal=new PrincipalRef(PrincipalRef.PrincipalType.AGENT,agentId);
        TenantRef tenant=TenantRef.tenant(task.getTenantId());
        AuthenticationContext authentication=new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.AGENT,agentId),principal,tenant,Optional.empty(),
                new AuthenticationAssurance(AuthenticationAssurance.Level.SYSTEM,java.util.Set.of("AGENT_SERVICE"),now),
                com.opensocket.aievent.core.iam.security.contract.SecurityEpoch.ZERO,Optional.empty(),now,now.plus(5,ChronoUnit.MINUTES));
        AuthorizationDecision decision=authorization.evaluate(new AuthorizationRequest(principal,authentication,tenant,
                new ResourceAction("task.execute",ResourceAction.ActionKind.EXECUTE,true),
                new ResourceRef(task.getTenantId(),ResourceType.TASK,taskId),VisibilityLevel.STANDARD,RequestChannel.INTERNAL_PORT,
                purpose==null||purpose.isBlank()?"RS4_AGENT_TASK_EXECUTION":purpose,OperationPhase.START,"",
                correlationId==null||correlationId.isBlank()?"rs4-agent-task-runtime":correlationId,SecurityEpoch.ZERO,
                Map.of("runtimePrincipal","AGENT","agentId",agentId)));
        if(decision.effect()!=DecisionEffect.ALLOW){
            if(enforce) throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Agent is not authorized for this Task runtime operation.");
            log.warn("rs4_agent_task_runtime_shadow_denied taskId={} agentId={} decisionId={} reasons={}",taskId,agentId,decision.decisionId(),decision.reasons());
        }
        return decision;
    }
}
