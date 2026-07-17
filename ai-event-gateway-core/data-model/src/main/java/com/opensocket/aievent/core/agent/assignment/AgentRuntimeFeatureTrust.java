package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRuntimeFeatureTrust {
    private String tenantId;
    private String trustId;
    private String agentId;
    private String runtimeId;
    private String bindingId;
    private String featureCode;
    private String featureName;
    private AgentRuntimeFeatureTrustStatus trustStatus = AgentRuntimeFeatureTrustStatus.OBSERVED;
    private String source = "OBSERVATION";
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
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
