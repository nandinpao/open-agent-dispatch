package com.opensocket.aievent.core.agent.governance;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.MachineAccessBoundary;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.MachineCredentialRef;
import com.opensocket.aievent.core.iam.security.contract.MachinePrincipal;
import com.opensocket.aievent.core.iam.security.contract.MachinePrincipalType;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;

/** Projects a successfully governed Agent connection into the canonical Machine IAM contract. */
public final class AgentMachineAuthenticationFactory {
    private static final Duration MAX_RUNTIME_CONTEXT_TTL = Duration.ofMinutes(5);

    public MachineAuthenticationContext create(AgentConnectionAuthorizationResult result, Instant authenticatedAt) {
        if (result == null || result.getDecision() != AgentAuthorizationDecision.ALLOW) {
            throw new IllegalArgumentException("authorized Agent connection result is required");
        }
        Instant now = authenticatedAt == null ? Instant.now() : authenticatedAt;
        String agentId = required(result.getPrincipalId(), "principalId");
        String tenantId = required(result.getTenantId(), "tenantId");
        String credentialId = required(result.getCredentialId(), "credentialId");

        MachinePrincipal principal = new MachinePrincipal(agentId, MachinePrincipalType.AGENT, TenantRef.tenant(tenantId));
        MachineCredentialRef credential = new MachineCredentialRef(
                credentialId,
                MachineCredentialRef.CredentialType.AGENT_CREDENTIAL,
                "agent-credential-v" + Math.max(1, result.getCredentialVersion()),
                "opendispatch-agent-governance");

        TreeSet<String> systems = new TreeSet<>(result.getAllowedSystemCodes());
        Map<String, String> restrictions = new LinkedHashMap<>();
        put(restrictions, "agentId", agentId);
        put(restrictions, "ownerDepartmentId", result.getOwnerDepartmentId());
        put(restrictions, "ownerGroupId", result.getOwnerGroupId());
        put(restrictions, "capabilities", csv(result.getCapabilities()));
        put(restrictions, "allowedTaskTypes", csv(result.getAllowedTaskTypes()));
        put(restrictions, "policyVersion", Integer.toString(result.getPolicyVersion()));
        put(restrictions, "credentialVersion", Integer.toString(result.getCredentialVersion()));

        MachineAccessBoundary boundary = new MachineAccessBoundary(
                Set.of(),
                Set.of("agent.runtime"),
                Set.of("opendispatch-agent-runtime"),
                systems,
                Set.of(),
                Set.of(),
                restrictions);

        Instant expiresAt = now.plus(MAX_RUNTIME_CONTEXT_TTL);
        OffsetDateTime credentialExpiry = result.getCredentialExpiresAt();
        if (credentialExpiry != null && credentialExpiry.toInstant().isBefore(expiresAt)) {
            expiresAt = credentialExpiry.toInstant();
        }
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("Agent credential is already expired");
        long principalEpoch = Math.max(result.getPolicyVersion(), result.getCredentialVersion());

        return new MachineAuthenticationContext(
                principal,
                credential,
                boundary,
                new AuthenticationAssurance(AuthenticationAssurance.Level.SYSTEM, Set.of("AGENT_CREDENTIAL"), now),
                new SecurityEpoch(0, 0, Math.max(0, principalEpoch)),
                now,
                expiresAt);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String csv(Iterable<String> values) {
        TreeSet<String> clean = new TreeSet<>();
        if (values != null) for (String value : values) if (value != null && !value.isBlank()) clean.add(value.trim());
        return String.join(",", clean);
    }
    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value.trim());
    }
}
