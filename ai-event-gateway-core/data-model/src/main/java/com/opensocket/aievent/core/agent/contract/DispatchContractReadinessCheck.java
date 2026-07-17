package com.opensocket.aievent.core.agent.contract;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchContractReadinessCheck {
    private String code;
    private String status;
    private String message;
    private boolean blocking;
    private String nextAction;
    private Map<String, Object> details = new LinkedHashMap<>();

    public static DispatchContractReadinessCheck pass(String code, String message) {
        return of(code, "PASS", message, false, null);
    }

    public static DispatchContractReadinessCheck block(String code, String message, String nextAction) {
        return of(code, "BLOCKED", message, true, nextAction);
    }

    public static DispatchContractReadinessCheck warn(String code, String message, String nextAction) {
        return of(code, "WARN", message, false, nextAction);
    }

    public static DispatchContractReadinessCheck info(String code, String message) {
        return of(code, "INFO", message, false, null);
    }

    public static DispatchContractReadinessCheck of(String code, String status, String message, boolean blocking, String nextAction) {
        DispatchContractReadinessCheck check = new DispatchContractReadinessCheck();
        check.setCode(code);
        check.setStatus(status);
        check.setMessage(message);
        check.setBlocking(blocking);
        check.setNextAction(nextAction);
        return check;
    }

    public DispatchContractReadinessCheck withDetail(String key, Object value) {
        if (key != null && !key.isBlank() && value != null) {
            details.put(key, value);
        }
        return this;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }
}
