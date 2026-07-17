package com.opensocket.aievent.core.agent.setup;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSetupReadinessResponse {
    private String tenantId;
    private String agentId;
    private boolean ready;
    private String status;
    private String summary;
    private List<String> blockingReasons = new ArrayList<>();
    private List<AgentSetupReadinessCheck> checks = new ArrayList<>();
    private AgentSetupStartCommand startCommand;
    private List<String> profileCapabilities = new ArrayList<>();
    private List<String> runtimeReportedCapabilities = new ArrayList<>();
    private List<String> missingRuntimeCapabilities = new ArrayList<>();
    private List<String> extraRuntimeCapabilities = new ArrayList<>();
    private List<AgentSetupTroubleshootingStep> troubleshooting = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public void setBlockingReasons(List<String> blockingReasons) {
        this.blockingReasons = blockingReasons == null ? new ArrayList<>() : new ArrayList<>(blockingReasons);
    }

    public void setChecks(List<AgentSetupReadinessCheck> checks) {
        this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks);
    }

    public void setProfileCapabilities(List<String> profileCapabilities) {
        this.profileCapabilities = profileCapabilities == null ? new ArrayList<>() : new ArrayList<>(profileCapabilities);
    }

    public void setRuntimeReportedCapabilities(List<String> runtimeReportedCapabilities) {
        this.runtimeReportedCapabilities = runtimeReportedCapabilities == null ? new ArrayList<>() : new ArrayList<>(runtimeReportedCapabilities);
    }

    public void setMissingRuntimeCapabilities(List<String> missingRuntimeCapabilities) {
        this.missingRuntimeCapabilities = missingRuntimeCapabilities == null ? new ArrayList<>() : new ArrayList<>(missingRuntimeCapabilities);
    }

    public void setExtraRuntimeCapabilities(List<String> extraRuntimeCapabilities) {
        this.extraRuntimeCapabilities = extraRuntimeCapabilities == null ? new ArrayList<>() : new ArrayList<>(extraRuntimeCapabilities);
    }

    public void setTroubleshooting(List<AgentSetupTroubleshootingStep> troubleshooting) {
        this.troubleshooting = troubleshooting == null ? new ArrayList<>() : new ArrayList<>(troubleshooting);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
