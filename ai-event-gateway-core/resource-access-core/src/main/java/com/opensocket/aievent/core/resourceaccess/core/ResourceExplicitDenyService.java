package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Objects;
import java.util.Set;

/** Explicit-deny governance. Only independently approved ACTIVE denies participate in precedence. */
public final class ResourceExplicitDenyService {
    private final ResourcePolicyRepository repository;
    public ResourceExplicitDenyService(ResourcePolicyRepository repository){this.repository=Objects.requireNonNull(repository);}
    public ExplicitDenyRecord find(String tenantId,String denyId){return repository.findExplicitDeny(tenantId,denyId).orElseThrow(()->new IllegalArgumentException("SCOPE_DENY_NOT_FOUND"));}
    public ExplicitDenyRecord create(CreateExplicitDenyCommand command){
        return repository.findExplicitDenyByIdempotencyKey(command.tenantId(),command.idempotencyKey()).orElseGet(()->{
            ExplicitDenyRecord deny=new ExplicitDenyRecord(command.tenantId(),command.denyId(),command.principalType(),command.principalId(),command.permissionCode(),command.resourceType(),command.scopeType(),command.scopeRefId(),command.denyReason(),command.severity(),command.validFrom(),command.validTo(),command.actorId(),"",ScopeDenyState.DRAFT,command.idempotencyKey(),1,command.requestedAt(),command.requestedAt());
            return repository.insertExplicitDeny(deny,command.correlationId());
        });
    }
    public ExplicitDenyRecord submit(ExplicitDenyMutationCommand command){return transition(command,Set.of(ScopeDenyState.DRAFT),ScopeDenyState.PENDING_APPROVAL,false);}
    public ExplicitDenyRecord approve(ExplicitDenyMutationCommand command){
        ExplicitDenyRecord current=require(command);if(current.createdBy().equals(command.actorId()))throw new IllegalStateException("SEPARATION_OF_DUTIES_VIOLATION");
        if(current.validTo()!=null&&!command.requestedAt().isBefore(current.validTo()))throw new IllegalStateException("SCOPE_DENY_ALREADY_EXPIRED");
        return transition(current,command,Set.of(ScopeDenyState.PENDING_APPROVAL),ScopeDenyState.ACTIVE,command.actorId());
    }
    public ExplicitDenyRecord revoke(ExplicitDenyMutationCommand command){return transition(command,Set.of(ScopeDenyState.DRAFT,ScopeDenyState.PENDING_APPROVAL,ScopeDenyState.ACTIVE),ScopeDenyState.REVOKED,true);}
    public ExplicitDenyRecord expire(ExplicitDenyMutationCommand command){
        ExplicitDenyRecord current=require(command);if(current.validTo()==null||command.requestedAt().isBefore(current.validTo()))throw new IllegalStateException("SCOPE_DENY_NOT_EXPIRED");
        return transition(current,command,Set.of(ScopeDenyState.PENDING_APPROVAL,ScopeDenyState.ACTIVE),ScopeDenyState.EXPIRED,current.approvedBy());
    }
    private ExplicitDenyRecord transition(ExplicitDenyMutationCommand c,Set<ScopeDenyState> expected,ScopeDenyState target,boolean retainApprover){ExplicitDenyRecord current=require(c);return transition(current,c,expected,target,retainApprover?current.approvedBy():"");}
    private ExplicitDenyRecord transition(ExplicitDenyRecord current,ExplicitDenyMutationCommand c,Set<ScopeDenyState> expected,ScopeDenyState target,String approvedBy){
        if(current.version()!=c.expectedVersion())throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");if(!expected.contains(current.state()))throw new IllegalStateException("SCOPE_DENY_INVALID_TRANSITION");
        return repository.transitionExplicitDeny(current,target,approvedBy,c.actorId(),c.reason(),c.correlationId(),c.idempotencyKey(),c.requestedAt());
    }
    private ExplicitDenyRecord require(ExplicitDenyMutationCommand command){return repository.findExplicitDeny(command.tenantId(),command.denyId()).orElseThrow(()->new IllegalArgumentException("SCOPE_DENY_NOT_FOUND"));}
}
