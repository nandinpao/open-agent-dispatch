package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** P4RA-C precedence primitive. Any matching effective deny blocks every allow source. */
public final class ExplicitDenyPrecedencePolicy {
    public Optional<ExplicitDenyRecord> blockingDeny(List<ExplicitDenyRecord> denies,String permissionCode,Instant evaluatedAt){
        if(denies==null||denies.isEmpty())return Optional.empty();
        return denies.stream().filter(deny->deny.effectiveAt(evaluatedAt)&&deny.coversPermission(permissionCode))
                .max(Comparator.comparingInt(deny->severityRank(deny.severity())));
    }
    public boolean denyOverridesGrant(ExplicitDenyRecord deny,ScopeGrantRecord grant,String requestedPermission,Instant evaluatedAt){
        return deny!=null&&grant!=null&&deny.tenantId().equals(grant.tenantId())&&deny.principalType()==grant.principalType()
                &&deny.principalId().equals(grant.principalId())&&deny.resourceType()==grant.resourceType()
                &&deny.effectiveAt(evaluatedAt)&&deny.coversPermission(requestedPermission);
    }
    private int severityRank(DenySeverity severity){return switch(severity){case LOW->0;case MEDIUM->1;case HIGH->2;case CRITICAL->3;};}
}
