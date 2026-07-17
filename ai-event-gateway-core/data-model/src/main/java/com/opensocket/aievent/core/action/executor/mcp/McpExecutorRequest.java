package com.opensocket.aievent.core.action.executor.mcp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.opensocket.aievent.core.action.AdapterAction;

@Getter
@Setter
@NoArgsConstructor
public class McpExecutorRequest {
    private String actionId;
    private String incidentId;
    private String taskId;
    private String actionType;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public static McpExecutorRequest from(AdapterAction action) {
        McpExecutorRequest request = new McpExecutorRequest();
        request.setActionId(action.getActionId());
        request.setIncidentId(action.getIncidentId());
        request.setTaskId(action.getTaskId());
        request.setActionType(action.getActionType() == null ? null : action.getActionType().name());
        request.setPayload(action.getPayload());
        return request;
    }
    public void setPayload(Map<String, Object> payload) { this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload); }
}
