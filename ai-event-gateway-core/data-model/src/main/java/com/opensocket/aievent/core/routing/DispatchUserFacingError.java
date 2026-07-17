package com.opensocket.aievent.core.routing;

import java.util.LinkedHashMap;
import java.util.Map;

public class DispatchUserFacingError {
    private DispatchUserFacingErrorCode code;
    private String severity;
    private String message;
    private String nextAction;
    private String runbookRef;
    private Map<String, Object> context = new LinkedHashMap<>();
    private Map<String, Object> technicalDetails = new LinkedHashMap<>();

    public DispatchUserFacingError() {
    }

    public DispatchUserFacingError(DispatchUserFacingErrorCode code,
                                   String severity,
                                   String message,
                                   String nextAction,
                                   String runbookRef,
                                   Map<String, Object> context,
                                   Map<String, Object> technicalDetails) {
        this.code = code;
        this.severity = severity;
        this.message = message;
        this.nextAction = nextAction;
        this.runbookRef = runbookRef;
        setContext(context);
        setTechnicalDetails(technicalDetails);
    }

    public static DispatchUserFacingError of(DispatchUserFacingErrorCode code,
                                             String severity,
                                             String message,
                                             String nextAction,
                                             String runbookRef,
                                             Map<String, Object> context,
                                             Map<String, Object> technicalDetails) {
        return new DispatchUserFacingError(code, severity, message, nextAction, runbookRef, context, technicalDetails);
    }

    public String toLegacyDecisionReason() {
        StringBuilder builder = new StringBuilder();
        if (code != null) {
            builder.append(code.name()).append(": ");
        }
        if (message != null && !message.isBlank()) {
            builder.append(message.trim());
        }
        if (nextAction != null && !nextAction.isBlank()) {
            builder.append(" 下一步：").append(nextAction.trim());
        }
        if (technicalDetails != null && !technicalDetails.isEmpty()) {
            builder.append(" Technical details: ").append(technicalDetails);
        }
        return builder.toString().trim();
    }

    public DispatchUserFacingErrorCode getCode() { return code; }
    public void setCode(DispatchUserFacingErrorCode code) { this.code = code; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public String getRunbookRef() { return runbookRef; }
    public void setRunbookRef(String runbookRef) { this.runbookRef = runbookRef; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context == null ? new LinkedHashMap<>() : new LinkedHashMap<>(context); }
    public Map<String, Object> getTechnicalDetails() { return technicalDetails; }
    public void setTechnicalDetails(Map<String, Object> technicalDetails) { this.technicalDetails = technicalDetails == null ? new LinkedHashMap<>() : new LinkedHashMap<>(technicalDetails); }
}
