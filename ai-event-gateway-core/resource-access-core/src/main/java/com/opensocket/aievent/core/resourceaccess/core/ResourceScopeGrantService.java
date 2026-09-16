package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;

import java.util.Objects;
import java.util.Set;

/** Governance lifecycle for explicit grants. P4RA-C does not consume these grants in business authorization. */
public final class ResourceScopeGrantService {
    private final ResourcePolicyRepository repository;
    public ResourceScopeGrantService(ResourcePolicyRepository repository){this.repository=Objects.requireNonNull(repository);}
    public ScopeGrantRecord find(String tenantId,String grantId){return repository.findScopeGrant(tenantId,grantId).orElseThrow(()->new IllegalArgumentException("SCOPE_GRANT_NOT_FOUND"));}
    public ScopeGrantRecord create(CreateScopeGrantCommand command){
        return repository.findScopeGrantByIdempotencyKey(command.tenantId(),command.idempotencyKey()).orElseGet(()->{
            ScopeGrantRecord grant=new ScopeGrantRecord(command.tenantId(),command.grantId(),command.principalType(),command.principalId(),command.permissionCode(),command.resourceType(),command.scopeType(),command.scopeRefId(),command.visibilityLevel(),command.validFrom(),command.validTo(),command.grantSource(),command.grantReason(),command.actorId(),"",ScopeGrantState.DRAFT,command.idempotencyKey(),1,command.requestedAt(),command.requestedAt());
            return repository.insertScopeGrant(grant,command.correlationId());
        });
    }
    public ScopeGrantRecord submit(ScopeGrantMutationCommand command){return transition(command,Set.of(ScopeGrantState.DRAFT),ScopeGrantState.PENDING_APPROVAL,false);}
    public ScopeGrantRecord approve(ScopeGrantMutationCommand command){
        ScopeGrantRecord current=require(command);if(current.createdBy().equals(command.actorId()))throw new IllegalStateException("SEPARATION_OF_DUTIES_VIOLATION");
        if(current.validTo()!=null&&!command.requestedAt().isBefore(current.validTo()))throw new IllegalStateException("SCOPE_GRANT_ALREADY_EXPIRED");
        return transition(current,command,Set.of(ScopeGrantState.PENDING_APPROVAL),ScopeGrantState.ACTIVE,command.actorId());
    }
    public ScopeGrantRecord suspend(ScopeGrantMutationCommand command){return transition(command,Set.of(ScopeGrantState.ACTIVE),ScopeGrantState.SUSPENDED,true);}
    public ScopeGrantRecord resume(ScopeGrantMutationCommand command){return transition(command,Set.of(ScopeGrantState.SUSPENDED),ScopeGrantState.ACTIVE,true);}
    public ScopeGrantRecord revoke(ScopeGrantMutationCommand command){return transition(command,Set.of(ScopeGrantState.DRAFT,ScopeGrantState.PENDING_APPROVAL,ScopeGrantState.ACTIVE,ScopeGrantState.SUSPENDED),ScopeGrantState.REVOKED,true);}
    public ScopeGrantRecord expire(ScopeGrantMutationCommand command){
        ScopeGrantRecord current=require(command);if(current.validTo()==null||command.requestedAt().isBefore(current.validTo()))throw new IllegalStateException("SCOPE_GRANT_NOT_EXPIRED");
        return transition(current,command,Set.of(ScopeGrantState.PENDING_APPROVAL,ScopeGrantState.ACTIVE,ScopeGrantState.SUSPENDED),ScopeGrantState.EXPIRED,current.approvedBy());
    }
    private ScopeGrantRecord transition(ScopeGrantMutationCommand c,Set<ScopeGrantState> expected,ScopeGrantState target,boolean retainApprover){ScopeGrantRecord current=require(c);return transition(current,c,expected,target,retainApprover?current.approvedBy():"");}
    private ScopeGrantRecord transition(ScopeGrantRecord current,ScopeGrantMutationCommand c,Set<ScopeGrantState> expected,ScopeGrantState target,String approvedBy){
        if(current.version()!=c.expectedVersion())throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");if(!expected.contains(current.state()))throw new IllegalStateException("SCOPE_GRANT_INVALID_TRANSITION");
        return repository.transitionScopeGrant(current,target,approvedBy,c.actorId(),c.reason(),c.correlationId(),c.idempotencyKey(),c.requestedAt());
    }
    private ScopeGrantRecord require(ScopeGrantMutationCommand c){return repository.findScopeGrant(c.tenantId(),c.grantId()).orElseThrow(()->new IllegalArgumentException("SCOPE_GRANT_NOT_FOUND"));}
}
