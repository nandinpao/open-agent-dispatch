package com.opensocket.aievent.core.action.executor.issue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutor;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class IssueTrackingAdapterActionExecutor implements AdapterActionExecutor {
    private final AdapterActionExecutionProperties properties;
    private final IssueVendorResolver vendorResolver;
    private final ObjectMapper mapper;
    private RedmineConnectorRuntimeService connectorRuntimeService;

    @Autowired
    public IssueTrackingAdapterActionExecutor(AdapterActionExecutionProperties properties,
                                              IssueVendorResolver vendorResolver) {
        this(properties, vendorResolver, JsonMapper.builder().build());
    }

    public IssueTrackingAdapterActionExecutor(AdapterActionExecutionProperties properties,
                                              IssueVendorResolver vendorResolver,
                                              ObjectMapper mapper) {
        this.properties = properties;
        this.vendorResolver = vendorResolver;
        this.mapper = mapper;
    }

    @Autowired(required = false)
    public void setConnectorRuntimeService(RedmineConnectorRuntimeService connectorRuntimeService) {
        this.connectorRuntimeService = connectorRuntimeService;
    }

    @Override
    public String name() {
        return "issue-tracking-executor-router";
    }

    @Override
    public boolean supports(AdapterAction action) {
        return action != null && action.getAdapterType() == AdapterType.ISSUE_TRACKING;
    }

    @Override
    public AdapterExecutionResult execute(AdapterAction action) {
        IssueVendor configured = vendorResolver.resolve(action);
        if (configured == IssueVendor.MOCK && properties.getMock().isEnabled()) {
            AdapterExecutionResult result = AdapterExecutionResult.success(name(), responseRef(IssueExecutorResponse.builder()
                    .success(true)
                    .vendor(IssueVendor.MOCK.name())
                    .issueId("mock-issue-" + action.getIncidentId())
                    .issueStatus("mock_synced")
                    .responseRef("mock-issue-response:" + action.getActionId())
                    .build(), action));
            result.setIssueVendor(IssueVendor.MOCK.name());
            result.setIssueId("mock-issue-" + action.getIncidentId());
            result.setIssueStatus("mock_synced");
            return result;
        }
        if (connectorRuntimeService != null) {
            return connectorRuntimeService.execute(action);
        }
        return AdapterExecutionResult.permanentFailure(name(),
                "Redmine Connector Runtime is unavailable. Legacy scoped Issue authorization is not a production fallback.");
    }


    private String responseRef(IssueExecutorResponse response, AdapterAction action) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("vendor", response.getVendor());
            map.put("issueId", response.getIssueId());
            map.put("issueUrl", response.getIssueUrl());
            map.put("issueStatus", response.getIssueStatus());
            map.put("commentId", response.getCommentId());
            map.put("responseRef", response.getResponseRef());
            map.put("adapterActionId", action == null ? null : action.getActionId());
            map.put("idempotencyKey", action == null ? null : action.getIdempotencyKey());
            return mapper.writeValueAsString(map);
        } catch (Exception ignored) {
            return response.getResponseRef();
        }
    }
}
