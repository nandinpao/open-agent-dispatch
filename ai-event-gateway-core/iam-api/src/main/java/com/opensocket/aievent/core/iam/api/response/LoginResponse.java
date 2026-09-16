package com.opensocket.aievent.core.iam.api.response;

import java.util.List;

public record LoginResponse(
        String state,
        String challengeId,
        SessionResponse session,
        List<TenantChoiceResponse> tenantChoices,
        List<String> requiredActions,
        long credentialVersion) {
    public LoginResponse {
        tenantChoices = tenantChoices == null ? List.of() : List.copyOf(tenantChoices);
        requiredActions = requiredActions == null ? List.of() : List.copyOf(requiredActions);
    }
}
