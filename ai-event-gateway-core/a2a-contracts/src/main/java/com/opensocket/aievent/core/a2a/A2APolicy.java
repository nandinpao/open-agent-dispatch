package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2APolicy {
    private String tenantId;
    private String policyId;
    private String policyCode;
    private String policyName;
    private String sourceDomainId;
    private String sourceDepartmentId;
    private String sourceGroupId;
    private String sourceAgentPoolId;
    private String sourceAgentId;
    private String targetDomainId;
    private String targetDepartmentId;
    private String targetGroupId;
    private String targetAgentPoolId;
    private String governanceOwnerDepartmentId;
    private String governanceOwnerGroupId;
    private A2AGovernanceScopeClass governanceScopeClass = A2AGovernanceScopeClass.TENANT;
    private String governanceScopeStatus = "RESOLVED";
    private List<String> allowedTaskTypes = List.of();
    private List<String> allowedServiceCodes = List.of();
    private List<String> allowedCapabilityCodes = List.of();
    private String maxSensitivityLevel = "INTERNAL";
    private A2AApprovalMode approvalMode = A2AApprovalMode.NONE;
    private int maxHopCount = 3;
    private int rateLimitPerMinute = 60;
    private int timeoutSeconds = 300;
    private String issueProjectionPolicy = "NONE";
    private String handoffContextPolicyId = "DEFAULT_HANDOFF";
    private String handoffContextRequirement = "OPTIONAL";
    private A2AResultAggregationPolicy resultAggregationPolicy = A2AResultAggregationPolicy.MANUAL_DECISION;
    private int aggregationQuorum = 1;
    private A2ACancellationPolicy cancellationPolicy = A2ACancellationPolicy.MANUAL_DECISION;
    private A2AFailurePropagationPolicy failurePropagationPolicy = A2AFailurePropagationPolicy.WAIT_HUMAN;
    private boolean allowReturnToExistingDomain;
    private boolean enabled = true;
    private OffsetDateTime effectiveAt;
    private OffsetDateTime expiresAt;
    private long version = 1L;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public boolean activeAt(OffsetDateTime now) {
        return enabled && (effectiveAt == null || !now.isBefore(effectiveAt)) && (expiresAt == null || now.isBefore(expiresAt));
    }
    public boolean allowsTaskType(String value) { return allows(allowedTaskTypes, value); }
    public boolean allowsServiceCode(String value) { return value == null || value.isBlank() || allows(allowedServiceCodes, value); }
    public boolean allowsCapabilities(List<String> values) {
        if (values == null || values.isEmpty()) return true;
        if (allowedCapabilityCodes == null || allowedCapabilityCodes.isEmpty()) return false;
        return values.stream().allMatch(value -> allows(allowedCapabilityCodes, value));
    }
    private boolean allows(List<String> values, String target) {
        if (target == null || target.isBlank()) return false;
        return values != null && values.stream().anyMatch(v -> "*".equals(v) || target.equalsIgnoreCase(v));
    }
}
