package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSecurityEnforcementPolicyPo {
    private String policyId;
    private String agentId;
    private boolean enabled;
    private String duplicateRuntimeMode;
    private boolean requireCredentialRotation;
    private boolean notifyEmail;
    private boolean notifySlack;
    private boolean notifySiem;
    private String emailRecipientsJson;
    private String slackChannelsJson;
    private String siemTopicsJson;
    private String metadataJson;
    private String updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
