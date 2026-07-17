package com.opensocket.aievent.core.routing.governance.eligibility;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentEligibilityShadowCheck {
    private String evaluatorCode;
    private EligibilityShadowCheckOutcome outcome = EligibilityShadowCheckOutcome.NOT_APPLICABLE;
    private String reasonCode;
    private String message;
    private Map<String, Object> details = new LinkedHashMap<>();

    public static AgentEligibilityShadowCheck of(String evaluatorCode,
                                                  EligibilityShadowCheckOutcome outcome,
                                                  String reasonCode,
                                                  String message) {
        AgentEligibilityShadowCheck check = new AgentEligibilityShadowCheck();
        check.setEvaluatorCode(evaluatorCode);
        check.setOutcome(outcome);
        check.setReasonCode(reasonCode);
        check.setMessage(message);
        return check;
    }

    public AgentEligibilityShadowCheck withDetail(String key, Object value) {
        if (key != null && !key.isBlank() && value != null) details.put(key, value);
        return this;
    }

    public boolean isBlocking() { return outcome == EligibilityShadowCheckOutcome.BLOCK; }

    public String getEvaluatorCode() { return evaluatorCode; }
    public void setEvaluatorCode(String evaluatorCode) { this.evaluatorCode = evaluatorCode; }
    public EligibilityShadowCheckOutcome getOutcome() { return outcome; }
    public void setOutcome(EligibilityShadowCheckOutcome outcome) { this.outcome = outcome == null ? EligibilityShadowCheckOutcome.NOT_APPLICABLE : outcome; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Object> getDetails() { return new LinkedHashMap<>(details); }
    public void setDetails(Map<String, Object> details) { this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details); }
}
