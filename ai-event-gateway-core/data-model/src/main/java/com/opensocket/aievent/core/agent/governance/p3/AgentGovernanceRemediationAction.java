package com.opensocket.aievent.core.agent.governance.p3;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentGovernanceRemediationAction {
    private String action;
    private String label;
    private String severity = "INFO";
    private String profileCode;
    private String qualificationId;
    private Map<String, Object> payload = new LinkedHashMap<>();
}
