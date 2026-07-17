package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentCredentialPo {
    private String credentialId;
    private String agentId;
    private String credentialType;
    private String publicKeyFingerprint;
    private String tokenHash;
    private int credentialVersion;
    private OffsetDateTime issuedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private String revokedReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
