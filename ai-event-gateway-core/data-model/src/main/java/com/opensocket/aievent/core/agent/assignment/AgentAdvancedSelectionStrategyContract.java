package com.opensocket.aievent.core.agent.assignment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Phase 9E Advanced Selection Strategy contract.
 *
 * These contracts describe readiness requirements for future strategies. They are
 * not registered in the Current SelectionStrategyRegistry and must not affect
 * Runtime Eligibility or Assignment selection until a strategy is explicitly
 * implemented, tested, simulated and enabled in a future release.
 */
@Getter
@Setter
public class AgentAdvancedSelectionStrategyContract {
    private String strategyCode;
    private String displayName;
    private String status = "CONTRACT_ONLY";
    private boolean productionEnabled = false;
    private boolean simulationRequired = true;
    private String simulationSupportStatus = "CONTRACT_REQUIRED_NOT_ENABLED";
    private String formula;
    private String stateStorage;
    private String concurrencyDefinition;
    private String fallbackStrategy = "LOWEST_LOAD";
    private String assignmentEvidenceContract;
    private String uiExplanation;
    private List<String> requiredReadinessChecks = List.of();
    private List<String> localityDimensions = List.of();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setRequiredReadinessChecks(List<String> requiredReadinessChecks) {
        this.requiredReadinessChecks = requiredReadinessChecks == null ? List.of() : List.copyOf(requiredReadinessChecks);
    }

    public void setLocalityDimensions(List<String> localityDimensions) {
        this.localityDimensions = localityDimensions == null ? List.of() : List.copyOf(localityDimensions);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
