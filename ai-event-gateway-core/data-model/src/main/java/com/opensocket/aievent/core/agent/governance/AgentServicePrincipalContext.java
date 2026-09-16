package com.opensocket.aievent.core.agent.governance;

import java.util.List;

/**
 * RS3 canonical runtime principal projected after Agent connection authorization.
 *
 * <p>This does not create a second IAM identity store. It is the runtime projection of the
 * governed Agent profile + credential + authorization-scope evidence. Dispatch/Task enforcement
 * consumes this context in RS4 instead of inferring authority from Agent name/capability.</p>
 */
public record AgentServicePrincipalContext(
        String principalType,
        String principalId,
        String tenantId,
        String ownerDepartmentId,
        String ownerGroupId,
        List<String> capabilities,
        List<String> allowedTaskTypes,
        List<String> allowedSystemCodes,
        int credentialVersion,
        int policyVersion) {
    public AgentServicePrincipalContext {
        principalType = principalType == null || principalType.isBlank() ? "AGENT_SERVICE" : principalType.trim();
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        allowedTaskTypes = allowedTaskTypes == null ? List.of() : List.copyOf(allowedTaskTypes);
        allowedSystemCodes = allowedSystemCodes == null ? List.of() : List.copyOf(allowedSystemCodes);
        credentialVersion = Math.max(0, credentialVersion);
        policyVersion = Math.max(0, policyVersion);
    }
}
