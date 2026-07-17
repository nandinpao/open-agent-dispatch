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
public class AgentConnectionRepairActionResult {
    private String agentId;
    private String actionCode;
    private String status;
    private String message;
    private AgentProfile profile;
    private AgentLatestAuthFailureResponse latestAuthFailure;
    private List<AgentConnectionRepairAction> nextActions = new ArrayList<>();
    private List<AgentSetupTroubleshootingStep> troubleshooting = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime occurredAt;

    public void setNextActions(List<AgentConnectionRepairAction> nextActions) {
        this.nextActions = nextActions == null ? new ArrayList<>() : new ArrayList<>(nextActions);
    }

    public void setTroubleshooting(List<AgentSetupTroubleshootingStep> troubleshooting) {
        this.troubleshooting = troubleshooting == null ? new ArrayList<>() : new ArrayList<>(troubleshooting);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
