package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchTaskDefinitionImpactPreview {
    private String tenantId;
    private String definitionId;
    private String sourceSystem;
    private String taskType;
    private String action;
    private boolean allowed = true;
    private String severity = "INFO";
    private String summary;
    private List<String> blockingReasons = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private Map<String, Integer> affectedCounts = new LinkedHashMap<>();
    private List<String> affectedProfileCodes = new ArrayList<>();
    private List<String> affectedActiveProfileCodes = new ArrayList<>();
    private List<String> affectedPolicyCodes = new ArrayList<>();
    private List<String> affectedRecipeCodes = new ArrayList<>();
    private OffsetDateTime generatedAt;
}
