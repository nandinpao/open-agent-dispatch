package com.opensocket.aievent.core.action;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Formats terminal Agent callback content for issue-tracking history.
 *
 * <p>Phase 3B intentionally keeps the full Agent result in Redmine/GitLab/Jira
 * comments. Admin UI should only surface a summary and the issue bridge state.</p>
 */
final class AgentIssueHistoryFormatter {
    private AgentIssueHistoryFormatter() {}

    static Map<String, Object> agentResult(TaskRecord task,
                                           DispatchRequest dispatch,
                                           Incident incident,
                                           TaskCallbackRequest callback,
                                           TaskCallbackType callbackType) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> callbackPayload = callback == null ? Map.of() : safeMap(callback.getPayload());
        Map<String, Object> resultPayload = nestedResult(callbackPayload);
        result.put("formatVersion", "phase3b-agent-result-history-v1");
        result.put("summary", summary(callback, resultPayload, callbackPayload));
        result.put("details", details(resultPayload, callbackPayload));
        result.put("findings", listValue(resultPayload, callbackPayload, "findings", "observations", "rootCauses", "causes"));
        result.put("recommendations", listValue(resultPayload, callbackPayload, "recommendations", "nextSteps", "suggestedActions", "actions"));
        result.put("evidence", evidence(resultPayload, callbackPayload));
        result.put("resultStatus", callback == null ? null : callback.getResultStatus());
        result.put("callbackType", callbackType == null ? null : callbackType.name());
        result.put("callbackId", callback == null ? null : callback.getCallbackId());
        result.put("occurredAt", occurredAt(callback));
        result.put("taskId", task == null ? null : task.getTaskId());
        result.put("incidentId", task == null ? null : task.getIncidentId());
        result.put("agentId", dispatch == null ? (callback == null ? null : callback.getAgentId()) : dispatch.getAgentId());
        result.put("linkedIssueId", incident == null ? null : incident.getLinkedIssueId());
        return result;
    }

    static String summary(TaskCallbackRequest callback) {
        return summary(callback,
                callback == null ? Map.of() : nestedResult(safeMap(callback.getPayload())),
                callback == null ? Map.of() : safeMap(callback.getPayload()));
    }

    static String issueDescription(TaskRecord task,
                                   DispatchRequest dispatch,
                                   Incident incident,
                                   TaskCallbackRequest callback,
                                   TaskCallbackType callbackType) {
        return "## OpenDispatch Issue Context\n\n"
                + contextBlock(task, dispatch, incident, callback, callbackType)
                + "\n---\n\n"
                + issueComment(task, dispatch, incident, callback, callbackType);
    }

    static String issueComment(TaskRecord task,
                               DispatchRequest dispatch,
                               Incident incident,
                               TaskCallbackRequest callback,
                               TaskCallbackType callbackType) {
        Map<String, Object> callbackPayload = callback == null ? Map.of() : safeMap(callback.getPayload());
        Map<String, Object> resultPayload = nestedResult(callbackPayload);
        StringBuilder out = new StringBuilder();
        out.append("## Agent Result Update\n\n");
        out.append("### Summary\n");
        out.append(nonBlank(summary(callback, resultPayload, callbackPayload), "Agent result is not available yet.")).append("\n\n");

        String details = details(resultPayload, callbackPayload);
        if (hasText(details)) {
            out.append("### Analysis\n").append(details).append("\n\n");
        }

        appendList(out, "### Findings", listValue(resultPayload, callbackPayload, "findings", "observations", "rootCauses", "causes"));
        appendList(out, "### Recommendations", listValue(resultPayload, callbackPayload, "recommendations", "nextSteps", "suggestedActions", "actions"));
        appendEvidence(out, evidence(resultPayload, callbackPayload));

        out.append("### Execution Context\n");
        out.append("- Result status: ").append(blank(callback == null ? null : callback.getResultStatus())).append("\n");
        out.append("- Callback type: ").append(blank(callbackType == null ? null : callbackType.name())).append("\n");
        out.append("- Callback ID: ").append(blank(callback == null ? null : callback.getCallbackId())).append("\n");
        out.append("- Task: ").append(blank(task == null ? null : task.getTaskId())).append("\n");
        out.append("- Incident: ").append(blank(task == null ? null : task.getIncidentId())).append("\n");
        out.append("- Agent: ").append(blank(dispatch == null ? (callback == null ? null : callback.getAgentId()) : dispatch.getAgentId())).append("\n");
        out.append("- Gateway: ").append(blank(dispatch == null ? (callback == null ? null : callback.getOwnerGatewayNodeId()) : dispatch.getOwnerGatewayNodeId())).append("\n");
        out.append("- Occurred at: ").append(blank(occurredAt(callback))).append("\n");
        return out.toString().trim();
    }

    private static String contextBlock(TaskRecord task,
                                       DispatchRequest dispatch,
                                       Incident incident,
                                       TaskCallbackRequest callback,
                                       TaskCallbackType callbackType) {
        StringBuilder out = new StringBuilder();
        out.append("- Incident: ").append(blank(task == null ? null : task.getIncidentId())).append("\n");
        out.append("- Task: ").append(blank(task == null ? null : task.getTaskId())).append("\n");
        out.append("- Task type: ").append(blank(task == null || task.getTaskType() == null ? null : task.getTaskType().name())).append("\n");
        out.append("- Priority: ").append(blank(task == null || task.getPriority() == null ? null : task.getPriority().name())).append("\n");
        out.append("- Severity: ").append(blank(incident == null || incident.getSeverity() == null ? null : incident.getSeverity().name())).append("\n");
        out.append("- Source system: ").append(blank(incident == null ? null : incident.getSourceSystem())).append("\n");
        out.append("- Source event: ").append(blank(task == null ? null : task.getSourceEventId())).append("\n");
        out.append("- Site / Plant: ").append(blank(task == null ? null : task.getSiteId())).append(" / ").append(blank(task == null ? null : task.getPlantId())).append("\n");
        out.append("- Object: ").append(blank(task == null ? null : task.getObjectType())).append(" / ").append(blank(task == null ? null : task.getObjectId())).append("\n");
        out.append("- Event / Error: ").append(blank(task == null ? null : task.getEventType())).append(" / ").append(blank(task == null ? null : task.getErrorCode())).append("\n");
        out.append("- Message: ").append(blank(incident == null ? null : incident.getLastMessage())).append("\n");
        out.append("- Occurrence count: ").append(incident == null ? "-" : incident.getOccurrenceCount()).append("\n");
        out.append("- First seen / Last seen: ").append(blank(incident == null ? null : String.valueOf(incident.getFirstSeenAt()))).append(" / ").append(blank(incident == null ? null : String.valueOf(incident.getLastSeenAt()))).append("\n");
        out.append("- Agent: ").append(blank(dispatch == null ? (callback == null ? null : callback.getAgentId()) : dispatch.getAgentId())).append("\n");
        out.append("- Dispatch request: ").append(blank(dispatch == null ? (callback == null ? null : callback.getDispatchRequestId()) : dispatch.getDispatchRequestId())).append("\n");
        out.append("- Callback: ").append(blank(callback == null ? null : callback.getCallbackId())).append(" / ").append(blank(callbackType == null ? null : callbackType.name())).append("\n");
        out.append("- Incident status: ").append(blank(incident == null || incident.getStatus() == null ? null : incident.getStatus().name())).append("\n");
        return out.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedResult(Map<String, Object> payload) {
        Object result = payload == null ? null : payload.get("result");
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return Map.of();
    }

    private static Map<String, Object> safeMap(Map<String, Object> payload) {
        return payload == null ? Map.of() : payload;
    }

    private static String summary(TaskCallbackRequest callback, Map<String, Object> result, Map<String, Object> payload) {
        return firstNonBlank(
                text(result, "summary", "agentSummary", "resultSummary", "conclusion"),
                text(payload, "summary", "agentSummary", "resultSummary"),
                text(nestedAgentCallback(payload), "message", "callbackMessage"),
                callback == null ? null : callback.getMessage(),
                callback == null ? null : callback.getResultStatus(),
                callback == null ? null : callback.getErrorMessage());
    }

    private static String details(Map<String, Object> result, Map<String, Object> payload) {
        return firstNonBlank(
                text(result, "details", "analysis", "rootCauseAnalysis", "diagnosticNotes", "report", "description"),
                text(payload, "details", "analysis", "rootCauseAnalysis", "diagnosticNotes", "report"));
    }

    private static List<Object> evidence(Map<String, Object> result, Map<String, Object> payload) {
        List<Object> values = new ArrayList<>();
        values.addAll(listValue(result, payload, "evidence", "evidenceItems", "metrics", "logs", "references"));
        Object mapEvidence = firstObject(result, payload, "evidence", "evidenceItems");
        if (values.isEmpty() && mapEvidence instanceof Map<?, ?>) values.add(mapEvidence);
        return values;
    }

    private static List<Object> listValue(Map<String, Object> result, Map<String, Object> payload, String... keys) {
        Object value = firstObject(result, payload, keys);
        if (value instanceof List<?> list) return new ArrayList<>(list);
        if (value instanceof Object[] array) return new ArrayList<>(List.of(array));
        if (value != null && hasText(String.valueOf(value))) return new ArrayList<>(List.of(value));
        return List.of();
    }

    private static Object firstObject(Map<String, Object> result, Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (result != null && result.get(key) != null) return result.get(key);
            if (payload != null && payload.get(key) != null) return payload.get(key);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedAgentCallback(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("agentCallback");
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return Map.of();
    }

    private static String text(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String s && !s.isBlank()) return s.trim();
            if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        }
        return null;
    }

    private static void appendList(StringBuilder out, String title, List<Object> values) {
        if (values == null || values.isEmpty()) return;
        out.append(title).append("\n");
        int index = 1;
        for (Object value : values) {
            out.append(index++).append(". ").append(renderValue(value)).append("\n");
        }
        out.append("\n");
    }

    private static void appendEvidence(StringBuilder out, List<Object> values) {
        if (values == null || values.isEmpty()) return;
        out.append("### Evidence\n");
        int index = 1;
        for (Object value : values) {
            out.append(index++).append(". ").append(renderValue(value)).append("\n");
        }
        out.append("\n");
    }

    private static String renderValue(Object value) {
        if (value == null) return "-";
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((key, item) -> {
                if (item != null) parts.add(String.valueOf(key) + ": " + String.valueOf(item));
            });
            return parts.isEmpty() ? "-" : String.join("; ", parts);
        }
        return String.valueOf(value);
    }

    private static String occurredAt(TaskCallbackRequest callback) {
        OffsetDateTime at = callback == null ? null : callback.getOccurredAt();
        return at == null ? null : at.toString();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (hasText(value)) return value.trim();
        return null;
    }

    private static String nonBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blank(String value) { return hasText(value) ? value : "-"; }
}
