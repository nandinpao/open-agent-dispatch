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
public class DispatchContractChainInspectionResponse {
    private String tenantId;
    private String sourceSystem;
    private String taskType;
    private String capabilityCode;
    private String profileCode;
    private String policyCode;
    private String agentId;
    private boolean healthy;
    private String status;
    private String summary;
    private String firstBlockingCode;
    private String firstBlockingReason;
    private String recommendedFix;
    private List<DispatchContractChainInspectionItem> items = new ArrayList<>();
    private Map<String, Object> diagnostics = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public void setItems(List<DispatchContractChainInspectionItem> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public void setDiagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(diagnostics);
    }
}
