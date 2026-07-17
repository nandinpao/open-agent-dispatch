package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.agent.setup.AgentSetupTroubleshootingStep;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentLatestAuthFailureResponse {
    private String agentId;
    private boolean hasFailure;
    private String securityEventId;
    private String eventType;
    private String denyReason;
    private String reason;
    private String summary;
    private String gatewayNodeId;
    private String claimedAgentId;
    private String remoteAddress;
    private OffsetDateTime occurredAt;
    private String securityEventLink;
    private List<AgentSetupTroubleshootingStep> troubleshooting = new ArrayList<>();
    private List<AgentConnectionRepairAction> repairActions = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public void setTroubleshooting(List<AgentSetupTroubleshootingStep> troubleshooting) {
        this.troubleshooting = troubleshooting == null ? new ArrayList<>() : new ArrayList<>(troubleshooting);
    }

    public void setRepairActions(List<AgentConnectionRepairAction> repairActions) {
        this.repairActions = repairActions == null ? new ArrayList<>() : new ArrayList<>(repairActions);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
