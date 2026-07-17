package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentConnectionRepairActionsResponse {
    public static final String SOURCE_OF_TRUTH = "CORE_AGENT_SECURITY_EVENTS";
    public static final String sourceOfTruth = SOURCE_OF_TRUTH;
    private String agentId;
    private boolean hasFailure;
    private String denyReason;
    private String securityEventId;
    private String summary;
    private List<AgentConnectionRepairAction> actions = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public void setActions(List<AgentConnectionRepairAction> actions) {
        this.actions = actions == null ? new ArrayList<>() : new ArrayList<>(actions);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
