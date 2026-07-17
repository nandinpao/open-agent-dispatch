package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentConnectionRepairActionCommand {
    private String operatorId;
    private String reason;
    private String credentialToken;
    private String credentialHash;
    private String publicKeyFingerprint;
    private OffsetDateTime credentialExpiresAt;
    private Boolean revokeExisting = Boolean.TRUE;
    private Boolean enableAfterRepair = Boolean.TRUE;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
