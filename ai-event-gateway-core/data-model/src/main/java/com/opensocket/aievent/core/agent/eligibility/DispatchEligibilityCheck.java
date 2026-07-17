package com.opensocket.aievent.core.agent.eligibility;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchEligibilityCheck {
    private String code;
    private String status = "INFO";
    private boolean blocking;
    private String message;
    private Map<String, Object> details = new LinkedHashMap<>();

    public static DispatchEligibilityCheck pass(String code, String message) {
        return of(code, "PASS", false, message);
    }

    public static DispatchEligibilityCheck warn(String code, String message) {
        return of(code, "WARN", false, message);
    }

    public static DispatchEligibilityCheck block(String code, String message) {
        return of(code, "BLOCKED", true, message);
    }

    public static DispatchEligibilityCheck info(String code, String message) {
        return of(code, "INFO", false, message);
    }

    public static DispatchEligibilityCheck of(String code, String status, boolean blocking, String message) {
        DispatchEligibilityCheck check = new DispatchEligibilityCheck();
        check.setCode(code);
        check.setStatus(status);
        check.setBlocking(blocking);
        check.setMessage(message);
        return check;
    }

    public DispatchEligibilityCheck withDetail(String key, Object value) {
        if (key != null && !key.isBlank() && value != null) {
            this.details.put(key, value);
        }
        return this;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }
}
