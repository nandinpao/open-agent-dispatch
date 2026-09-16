package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Objects;
import java.util.Set;

/** Principal clearance governance. Requester-supplied approver identifiers are never trusted. */
public final class PrincipalClearanceService {
    private final ResourcePolicyRepository repository;
    public PrincipalClearanceService(ResourcePolicyRepository repository){this.repository=Objects.requireNonNull(repository);}
    public PrincipalClearanceRecord find(String tenantId,String clearanceId){return repository.findPrincipalClearance(tenantId,clearanceId).orElseThrow(()->new IllegalArgumentException("CLEARANCE_NOT_FOUND"));}
    public PrincipalClearanceRecord create(GrantPrincipalClearanceCommand command){
        return repository.findPrincipalClearanceByIdempotencyKey(command.tenantId(),command.idempotencyKey()).orElseGet(()->repository.insertPrincipalClearance(
                new PrincipalClearanceRecord(command.tenantId(),command.clearanceId(),command.principalType(),command.principalId(),command.clearanceLevel(),command.validFrom(),command.validTo(),ClearanceStatus.DRAFT,"",command.reason(),1,command.requestedAt(),command.requestedAt()),command.actorId(),command.idempotencyKey(),command.correlationId()));
    }
    public PrincipalClearanceRecord submit(ClearanceMutationCommand command){return transition(command,Set.of(ClearanceStatus.DRAFT),ClearanceStatus.PENDING_APPROVAL,false);}
    public PrincipalClearanceRecord approve(ClearanceMutationCommand command){
        PrincipalClearanceRecord current=require(command);String createdBy=repository.findPrincipalClearanceCreator(command.tenantId(),command.clearanceId()).orElseThrow(()->new IllegalStateException("CLEARANCE_CREATOR_EVIDENCE_MISSING"));
        if(createdBy.equals(command.actorId()))throw new IllegalStateException("SEPARATION_OF_DUTIES_VIOLATION");
        if(current.validTo()!=null&&!command.requestedAt().isBefore(current.validTo()))throw new IllegalStateException("CLEARANCE_ALREADY_EXPIRED");
        return transition(current,command,Set.of(ClearanceStatus.PENDING_APPROVAL),ClearanceStatus.ACTIVE,command.actorId());
    }
    public PrincipalClearanceRecord suspend(ClearanceMutationCommand command){return transition(command,Set.of(ClearanceStatus.ACTIVE),ClearanceStatus.SUSPENDED,true);}
    public PrincipalClearanceRecord revoke(ClearanceMutationCommand command){return transition(command,Set.of(ClearanceStatus.DRAFT,ClearanceStatus.PENDING_APPROVAL,ClearanceStatus.ACTIVE,ClearanceStatus.SUSPENDED),ClearanceStatus.REVOKED,true);}
    public PrincipalClearanceRecord expire(ClearanceMutationCommand command){PrincipalClearanceRecord current=require(command);if(current.validTo()==null||command.requestedAt().isBefore(current.validTo()))throw new IllegalStateException("CLEARANCE_NOT_EXPIRED");return transition(current,command,Set.of(ClearanceStatus.PENDING_APPROVAL,ClearanceStatus.ACTIVE,ClearanceStatus.SUSPENDED),ClearanceStatus.EXPIRED,current.approvedBy());}
    private PrincipalClearanceRecord transition(ClearanceMutationCommand c,Set<ClearanceStatus> expected,ClearanceStatus target,boolean retainApprover){PrincipalClearanceRecord current=require(c);return transition(current,c,expected,target,retainApprover?current.approvedBy():"");}
    private PrincipalClearanceRecord transition(PrincipalClearanceRecord current,ClearanceMutationCommand c,Set<ClearanceStatus> expected,ClearanceStatus target,String approvedBy){if(current.version()!=c.expectedVersion())throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");if(!expected.contains(current.status()))throw new IllegalStateException("CLEARANCE_INVALID_TRANSITION");return repository.transitionPrincipalClearance(current,target,approvedBy,c.actorId(),c.reason(),c.correlationId(),c.idempotencyKey(),c.requestedAt());}
    private PrincipalClearanceRecord require(ClearanceMutationCommand command){return repository.findPrincipalClearance(command.tenantId(),command.clearanceId()).orElseThrow(()->new IllegalArgumentException("CLEARANCE_NOT_FOUND"));}
}
