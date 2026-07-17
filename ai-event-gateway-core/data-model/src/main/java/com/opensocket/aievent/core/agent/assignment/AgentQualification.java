package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentQualification {
    private String tenantId;
    private String qualificationId;
    private String agentId;
    private String profileCode;
    private AgentQualificationStatus qualificationStatus = AgentQualificationStatus.PENDING;
    private String evidenceType = "MANUAL";
    private String evidenceRef;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private OffsetDateTime expiresAt;
    private int grantedPolicyVersion = 1;
    private OffsetDateTime lastRenewedAt;
    private OffsetDateTime renewalDueAt;
    private String renewalStatus = "CURRENT";
    private String reason;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public boolean activeAt(OffsetDateTime now) {
        return qualificationStatus == AgentQualificationStatus.APPROVED
                && (expiresAt == null || (now != null && expiresAt.isAfter(now)));
    }

    public boolean renewalDueAt(OffsetDateTime now) {
        return renewalDueAt != null && now != null && !renewalDueAt.isAfter(now);
    }

    public boolean policyVersionBehind(AgentAssignmentProfile profile) {
        return profile != null && grantedPolicyVersion < profile.getPolicyVersion();
    }
}
