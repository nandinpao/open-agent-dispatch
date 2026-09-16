package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Objects;

public final class ResourceVisibilityPolicyService {
    private final ResourcePolicyRepository repository;
    public ResourceVisibilityPolicyService(ResourcePolicyRepository repository){this.repository=Objects.requireNonNull(repository);}
    public VisibilityPolicyRecord find(String tenantId,String policyId){return repository.findVisibilityPolicy(tenantId,policyId).orElseThrow(()->new IllegalArgumentException("VISIBILITY_POLICY_NOT_FOUND"));}
    public VisibilityPolicyRecord create(CreateVisibilityPolicyCommand command){
        return repository.findVisibilityPolicyByIdempotencyKey(command.tenantId(),command.idempotencyKey()).orElseGet(()->repository.insertVisibilityPolicy(
                new VisibilityPolicyRecord(command.tenantId(),command.policyId(),command.resourceType(),command.policyName(),command.maximumVisibility(),command.maximumSensitivity(),VisibilityPolicyState.DRAFT,command.fieldRules(),1,command.actorId(),command.actorId(),command.requestedAt(),command.requestedAt()),command.idempotencyKey(),command.correlationId()));
    }
    public VisibilityPolicyRecord activate(VisibilityPolicyMutationCommand command){return transition(command,VisibilityPolicyState.DRAFT,VisibilityPolicyState.ACTIVE);}
    public VisibilityPolicyRecord retire(VisibilityPolicyMutationCommand command){return transition(command,VisibilityPolicyState.ACTIVE,VisibilityPolicyState.RETIRED);}
    private VisibilityPolicyRecord transition(VisibilityPolicyMutationCommand command,VisibilityPolicyState expected,VisibilityPolicyState target){
        VisibilityPolicyRecord current=repository.findVisibilityPolicy(command.tenantId(),command.policyId()).orElseThrow(()->new IllegalArgumentException("VISIBILITY_POLICY_NOT_FOUND"));
        if(current.version()!=command.expectedVersion())throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");if(current.state()!=expected)throw new IllegalStateException("VISIBILITY_POLICY_INVALID_TRANSITION");
        if(target==VisibilityPolicyState.ACTIVE&&current.createdBy().equals(command.actorId()))throw new IllegalStateException("SEPARATION_OF_DUTIES_VIOLATION");
        return repository.transitionVisibilityPolicy(current,target,command.actorId(),command.reason(),command.correlationId(),command.idempotencyKey(),command.requestedAt());
    }
}
