package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Objects;

/** Security-state mutation increments the resource epoch and writes immutable audit evidence. */
public final class ResourceSecurityStateService {
    private final ResourcePolicyRepository repository;
    public ResourceSecurityStateService(ResourcePolicyRepository repository){this.repository=Objects.requireNonNull(repository);}
    public SecurityStateChangeResult change(SecurityStateChangeCommand command){
        return repository.findSecurityStateChange(command.resourceRef(),command.idempotencyKey()).orElseGet(()->{
            if(command.targetState()==ResourceSecurityState.ORPHANED)throw new IllegalStateException("ORPHAN_STATE_OWNED_BY_PROJECTION");
            if((command.targetState()==ResourceSecurityState.QUARANTINED||command.targetState()==ResourceSecurityState.INVESTIGATION)&&command.incidentId().isBlank())throw new IllegalStateException("INCIDENT_ID_REQUIRED");
            return repository.changeSecurityState(command);
        });
    }
}
