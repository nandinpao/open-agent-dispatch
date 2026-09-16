package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Authorizes the Human action only; Provider identity and credential selection remain in Issue Tracking. */
@Component
@ConditionalOnProperty(prefix="resource-access",name={"enabled","integration-enabled"},havingValue="true")
public final class DefaultExternalWriteAuthorizationService implements ExternalWriteAuthorizationPort {
    private final ResourceAccessEnforcementPort enforcement;
    private final ResourceEnforcementContextPort contexts;
    private final ExternalWriteAuthorizationAuditPort audit;
    public DefaultExternalWriteAuthorizationService(ResourceAccessEnforcementPort enforcement,ResourceEnforcementContextPort contexts,ObjectProvider<ExternalWriteAuthorizationAuditPort> audits){
        this.enforcement=Objects.requireNonNull(enforcement,"enforcement");this.contexts=Objects.requireNonNull(contexts,"contexts");this.audit=audits==null?null:audits.getIfAvailable();
    }
    @Override public ExternalWriteAuthorizationContext authorizeHumanWrite(ExternalWriteAuthorizationCommand command){
        Objects.requireNonNull(command,"command");
        AuthorizationDecision decision=enforcement.authorize(new ResourceEnforcementCommand(command.action(),command.resourceRef(),
                command.requestedVisibility(),RequestChannel.REST,command.purpose(),OperationPhase.BEFORE_SIDE_EFFECT,SecurityEpoch.ZERO,
                command.trustedFlowContext()));
        ResourceEnforcementContext runtime=contexts.current();
        boolean executable=decision.mode()==AuthorizationDecisionMode.FORMAL
                &&decision.effect()==DecisionEffect.ALLOW
                &&command.separationOfDutiesSatisfied()
                &&command.stepUpSatisfied();
        Map<String,String> evidence=new LinkedHashMap<>();
        evidence.put("decisionMode",decision.mode().name());evidence.put("decisionEffect",decision.effect().name());
        evidence.put("policyVersion",decision.policyVersion().toString());evidence.put("securityEpoch",decision.securityEpoch().toString());
        evidence.put("idempotencyKey",command.idempotencyKey());
        evidence.put("separationOfDutiesSatisfied",Boolean.toString(command.separationOfDutiesSatisfied()));
        evidence.put("stepUpSatisfied",Boolean.toString(command.stepUpSatisfied()));
        if(!command.separationOfDutiesSatisfied())evidence.put("blockingReason","SEPARATION_OF_DUTIES_VIOLATION");
        else if(!command.stepUpSatisfied())evidence.put("blockingReason","STEP_UP_REQUIRED");
        ExternalWriteAuthorizationContext context=new ExternalWriteAuthorizationContext(runtime.authentication().principal(),decision.decisionId(),command.resourceRef(),command.action(),
                command.purpose(),command.separationOfDutiesSatisfied(),command.stepUpSatisfied(),executable,runtime.correlationId(),
                runtime.requestedAt(),evidence);
        if(audit!=null)audit.append(command,context);return context;
    }
}
