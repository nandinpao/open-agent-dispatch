package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentQualificationCommand {
    private String tenantId;
    private String profileCode;
    private String evidenceType;
    private String evidenceRef;
    private String operatorId;
    private OffsetDateTime expiresAt;
    private String reason;
    /**
     * When true, the assignment request is immediately promoted to APPROVED.
     * This is intended for local/SIT bootstrap and explicit Admin UI inline approval.
     * Enterprise governance flows can keep this false to leave the service scope in PENDING review.
     */
    private boolean autoApprove;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
