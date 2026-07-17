package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRuntimeFeatureTrustPo {
    private String tenantId;
    private String trustId;
    private String agentId;
    private String runtimeId;
    private String bindingId;
    private String featureCode;
    private String featureName;
    private String trustStatus;
    private String source;
    private OffsetDateTime observedAt;
    private String verifiedBy;
    private OffsetDateTime verifiedAt;
    private String trustedBy;
    private OffsetDateTime trustedAt;
    private String revokedBy;
    private OffsetDateTime revokedAt;
    private OffsetDateTime expiresAt;
    private String evidenceRef;
    private String reason;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
