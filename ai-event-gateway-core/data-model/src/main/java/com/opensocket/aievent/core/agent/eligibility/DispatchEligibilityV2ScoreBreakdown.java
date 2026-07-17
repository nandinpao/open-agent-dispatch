package com.opensocket.aievent.core.agent.eligibility;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchEligibilityV2ScoreBreakdown {
    private String factorName;
    private int weight;
    private int contribution;
    private String direction;
    private String message;
    private Map<String, Object> details = new LinkedHashMap<>();

    public static DispatchEligibilityV2ScoreBreakdown of(String factorName, int weight, int contribution, String direction, String message) {
        DispatchEligibilityV2ScoreBreakdown item = new DispatchEligibilityV2ScoreBreakdown();
        item.setFactorName(factorName);
        item.setWeight(weight);
        item.setContribution(contribution);
        item.setDirection(direction);
        item.setMessage(message);
        return item;
    }

    public DispatchEligibilityV2ScoreBreakdown withDetail(String key, Object value) {
        if (key != null && !key.isBlank()) {
            details.put(key, value);
        }
        return this;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }
}
