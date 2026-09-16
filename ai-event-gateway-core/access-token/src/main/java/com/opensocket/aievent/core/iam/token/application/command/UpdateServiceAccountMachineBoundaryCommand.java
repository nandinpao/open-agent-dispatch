package com.opensocket.aievent.core.iam.token.application.command;

import com.opensocket.aievent.core.iam.token.domain.TokenScope;
import java.time.Duration;
import java.util.Set;

public record UpdateServiceAccountMachineBoundaryCommand(
        String tenantId,
        String serviceAccountId,
        String responsibilityBindingId,
        TokenScope restrictions,
        Set<String> machineScopes,
        Set<String> allowedSourceSystems,
        Duration credentialMaxTtl,
        int maxActiveCredentials,
        long expectedVersion,
        String actorId,
        String correlationId) {
    public UpdateServiceAccountMachineBoundaryCommand(String tenantId,String serviceAccountId,TokenScope restrictions,
            Set<String> machineScopes,Set<String> allowedSourceSystems,Duration credentialMaxTtl,int maxActiveCredentials,
            long expectedVersion,String actorId,String correlationId) {
        this(tenantId,serviceAccountId,"",restrictions,machineScopes,allowedSourceSystems,credentialMaxTtl,maxActiveCredentials,expectedVersion,actorId,correlationId);
    }
}
