package com.opensocket.aievent.core.agent.contract;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchContractChainInspectionRequest {
    private String tenantId;
    private String sourceSystem;
    private String taskType;
    private String capabilityCode;
    private String profileCode;
    private String policyCode;
    private String agentId;
    private String objectType;
    private String eventType;
    private String errorCode;
    private String message;
}
