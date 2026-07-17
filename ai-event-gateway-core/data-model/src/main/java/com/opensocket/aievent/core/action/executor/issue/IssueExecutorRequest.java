package com.opensocket.aievent.core.action.executor.issue;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.opensocket.aievent.core.action.AdapterAction;

@Getter
@Setter
@NoArgsConstructor
public class IssueExecutorRequest {
    private String actionId;
    private String incidentId;
    private String taskId;
    private String actionType;
    private String idempotencyKey;
    private IssueVendor vendor;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public static IssueExecutorRequest from(AdapterAction action, IssueVendor vendor) {
        IssueExecutorRequest request = new IssueExecutorRequest();
        request.setActionId(action.getActionId());
        request.setIncidentId(action.getIncidentId());
        request.setTaskId(action.getTaskId());
        request.setActionType(action.getActionType() == null ? null : action.getActionType().name());
        request.setIdempotencyKey(action.getIdempotencyKey());
        request.setVendor(vendor);
        request.setPayload(action.getPayload());
        return request;
    }
    public void setPayload(Map<String, Object> payload) { this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload); }
}
