package com.opensocket.aievent.core.agent.contract;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchContractTraceResponse {
    private String tenantId;
    private String taskId;
    private String sourceSystem;
    private String taskType;
    private String agentId;
    private String status;
    private boolean ready;
    private String summary;
    private String firstBlockingCode;
    private String firstBlockingReason;
    private Map<String, Object> capabilityResolution = new LinkedHashMap<>();
    private DispatchContractReadinessResponse readiness;
    private List<String> requiredCapabilities = new ArrayList<>();
    private List<DispatchContractReadinessCheck> checks = new ArrayList<>();
    private Map<String, Object> diagnostics = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public void setCapabilityResolution(Map<String, Object> capabilityResolution) {
        this.capabilityResolution = capabilityResolution == null ? new LinkedHashMap<>() : new LinkedHashMap<>(capabilityResolution);
    }

    public void setRequiredCapabilities(List<String> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities == null ? new ArrayList<>() : new ArrayList<>(requiredCapabilities);
    }

    public void setChecks(List<DispatchContractReadinessCheck> checks) {
        this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks);
    }

    public void setDiagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(diagnostics);
    }
}
