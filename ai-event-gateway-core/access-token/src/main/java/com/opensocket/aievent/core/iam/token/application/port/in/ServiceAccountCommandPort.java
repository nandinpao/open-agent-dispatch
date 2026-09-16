package com.opensocket.aievent.core.iam.token.application.port.in;

import com.opensocket.aievent.core.iam.token.application.command.*;
import com.opensocket.aievent.core.iam.token.domain.ServiceAccount;

public interface ServiceAccountCommandPort {
    ServiceAccount require(String tenantId, String serviceAccountId);
    ServiceAccount create(CreateServiceAccountCommand command);
    ServiceAccount updateMachineBoundary(UpdateServiceAccountMachineBoundaryCommand command);
    ServiceAccount reviewOwnership(ReviewServiceAccountOwnershipCommand command);
    ServiceAccount reconcileOwnership(ReconcileServiceAccountOwnershipCommand command);
    ServiceAccount suspendRisk(SuspendServiceAccountRiskCommand command);
}
