package com.opensocket.aievent.core.agent.governance.p3;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentPolicyDriftFinding {
    private String code;
    private String severity = "INFO";
    private String profileCode;
    private String qualificationId;
    private String message;
    private boolean blocking;
    private Map<String, Object> details = new LinkedHashMap<>();
}
