package com.opensocket.aievent.core.capability;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 WHO MAY policy.
 *
 * <p>This policy authorizes use of a Canonical Capability in a request context. It never
 * identifies a target Domain/System/Pool/Agent, never ranks providers and never selects
 * Netty/A2A/MCP transport. Provider filters are authorization attributes only.</p>
 */
public record DelegationPolicy(
        String tenantId,
        String policyId,
        String displayName,
        String description,
        String effect,
        String status,
        List<String> requesterPrincipalTypes,
        List<String> requesterDepartmentIds,
        List<String> requesterGroupIds,
        List<String> requesterRoleCodes,
        List<String> capabilityCodes,
        List<String> operations,
        Map<String, Object> resourceConstraints,
        List<String> allowedDataClasses,
        String maxSensitivityLevel,
        List<String> allowedAccessModes,
        List<String> requiredProviderTypes,
        List<String> requiredProviderCertifications,
        String approvalMode,
        BigDecimal maxEstimatedCost,
        Integer maxDelegationDepth,
        Integer maxAgentCalls,
        Long maxExecutionTimeMs,
        Integer priority,
        Integer version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public DelegationPolicy {
        requesterPrincipalTypes = copy(requesterPrincipalTypes);
        requesterDepartmentIds = copy(requesterDepartmentIds);
        requesterGroupIds = copy(requesterGroupIds);
        requesterRoleCodes = copy(requesterRoleCodes);
        capabilityCodes = copy(capabilityCodes);
        operations = copy(operations);
        resourceConstraints = resourceConstraints == null ? Map.of() : Map.copyOf(resourceConstraints);
        allowedDataClasses = copy(allowedDataClasses);
        allowedAccessModes = copy(allowedAccessModes);
        requiredProviderTypes = copy(requiredProviderTypes);
        requiredProviderCertifications = copy(requiredProviderCertifications);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
