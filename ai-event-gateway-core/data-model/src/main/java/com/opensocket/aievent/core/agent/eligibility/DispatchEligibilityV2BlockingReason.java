package com.opensocket.aievent.core.agent.eligibility;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchEligibilityV2BlockingReason {
    private String code;
    private String message;
    private String severity = "BLOCKING";
    private String policyCode;
    private String ruleId;
    private Map<String, Object> details = new LinkedHashMap<>();

    public static DispatchEligibilityV2BlockingReason of(String code, String message, String severity) {
        DispatchEligibilityV2BlockingReason reason = new DispatchEligibilityV2BlockingReason();
        reason.setCode(code);
        reason.setMessage(message);
        reason.setSeverity(severity == null || severity.isBlank() ? "BLOCKING" : severity);
        return reason;
    }

    public DispatchEligibilityV2BlockingReason withPolicy(String policyCode) {
        this.policyCode = policyCode;
        return this;
    }

    public DispatchEligibilityV2BlockingReason withRule(String ruleId) {
        this.ruleId = ruleId;
        return this;
    }

    public DispatchEligibilityV2BlockingReason withDetail(String key, Object value) {
        if (key != null && !key.isBlank()) {
            details.put(key, value);
        }
        return this;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }
}
