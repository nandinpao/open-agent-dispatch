package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class ResourceSecurityStateServiceTest {
    @Test void orphanStateCannotBeCreatedBySecurityMutation(){
        var service=new ResourceSecurityStateService(new InMemoryResourcePolicyRepository());
        var command=new SecurityStateChangeCommand(new ResourceRef("t",ResourceType.TASK,"1"),ResourceSecurityState.ORPHANED,1,"security","reason","","c","i",Instant.now());
        assertThrows(IllegalStateException.class,()->service.change(command));
    }
    @Test void quarantineRequiresIncident(){
        var service=new ResourceSecurityStateService(new InMemoryResourcePolicyRepository());
        var command=new SecurityStateChangeCommand(new ResourceRef("t",ResourceType.TASK,"1"),ResourceSecurityState.QUARANTINED,1,"security","reason","","c","i",Instant.now());
        assertThrows(IllegalStateException.class,()->service.change(command));
    }
}
