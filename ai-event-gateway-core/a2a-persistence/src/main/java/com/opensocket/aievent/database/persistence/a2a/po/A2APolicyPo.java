package com.opensocket.aievent.database.persistence.a2a.po;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2APolicyPo {
    private String tenantId; private String policyId; private String policyCode; private String policyName; private String sourceDomainId; private String sourceDepartmentId; private String sourceGroupId; private String sourceAgentPoolId; private String sourceAgentId; private String targetDomainId; private String targetDepartmentId; private String targetGroupId; private String targetAgentPoolId; private String governanceOwnerDepartmentId; private String governanceOwnerGroupId; private String governanceScopeClass; private String governanceScopeStatus; private String allowedTaskTypesJson; private String allowedServiceCodesJson; private String allowedCapabilityCodesJson; private String maxSensitivityLevel; private String approvalMode; private int maxHopCount; private int rateLimitPerMinute; private int timeoutSeconds; private String issueProjectionPolicy; private String handoffContextPolicyId; private String handoffContextRequirement; private String resultAggregationPolicy;
    private int aggregationQuorum; private String cancellationPolicy; private String failurePropagationPolicy; private boolean allowReturnToExistingDomain; private boolean enabled; private OffsetDateTime effectiveAt; private OffsetDateTime expiresAt; private long version; private OffsetDateTime createdAt; private OffsetDateTime updatedAt;
}
