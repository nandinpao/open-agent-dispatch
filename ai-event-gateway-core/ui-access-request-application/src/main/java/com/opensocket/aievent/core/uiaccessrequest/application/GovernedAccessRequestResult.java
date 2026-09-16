package com.opensocket.aievent.core.uiaccessrequest.application;

import com.opensocket.aievent.core.resourceaccess.contract.GovernedAccessRequestRecord;
import com.opensocket.aievent.core.resourceaccess.contract.ScopeGrantRecord;
import java.util.Objects;

public record GovernedAccessRequestResult(GovernedAccessRequestRecord request, ScopeGrantRecord grant) {
    public GovernedAccessRequestResult {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(grant, "grant");
        if (!request.scopeGrantId().equals(grant.grantId()) || !request.tenantId().equals(grant.tenantId()))
            throw new IllegalArgumentException("request and grant identity must match");
    }
}
