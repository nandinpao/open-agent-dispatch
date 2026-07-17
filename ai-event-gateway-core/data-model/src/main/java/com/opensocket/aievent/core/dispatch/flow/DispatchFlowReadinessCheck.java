package com.opensocket.aievent.core.dispatch.flow;

import java.util.LinkedHashMap;
import java.util.Map;

/** Single P2 Flow Rule readiness check displayed to beginner operators. */
public class DispatchFlowReadinessCheck {
    private String code;
    private String status;
    private String message;
    private Boolean blocking = false;
    private String nextAction;
    private Map<String, Object> details = new LinkedHashMap<>();

    public static DispatchFlowReadinessCheck of(String code, String status, String message, boolean blocking, String nextAction) {
        DispatchFlowReadinessCheck check = new DispatchFlowReadinessCheck();
        check.setCode(code);
        check.setStatus(status);
        check.setMessage(message);
        check.setBlocking(blocking);
        check.setNextAction(nextAction);
        return check;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Boolean getBlocking() { return blocking; }
    public void setBlocking(Boolean blocking) { this.blocking = blocking; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details); }
}
