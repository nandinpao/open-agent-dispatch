package com.opensocket.aievent.core.action.executor.issue;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutor;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.action.executor.AdapterExecutorUnavailableException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class IssueTrackingAdapterActionExecutor implements AdapterActionExecutor {
    private final AdapterActionExecutionProperties properties;
    private final IssueVendorResolver vendorResolver;
    private final ObjectMapper mapper;
    private final Map<IssueVendor, IssueTrackingActionExecutor> executors = new EnumMap<>(IssueVendor.class);

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
        executors.put(IssueVendor.JIRA, new MockCompatibleIssueVendorExecutor(IssueVendor.JIRA, properties.getIssue().getJiraExecutorName(), properties.getIssue().isJiraMockEnabled()));
        executors.put(IssueVendor.REDMINE, properties.getIssue().getRedmine().isEnabled()
                ? new RedmineIssueVendorExecutor(properties.getIssue().getRedmine(), properties.getIssue().getRedmineExecutorName(), mapper, properties.getExecutionTimeout())
                : new MockCompatibleIssueVendorExecutor(IssueVendor.REDMINE, properties.getIssue().getRedmineExecutorName(), properties.getIssue().isRedmineMockEnabled()));
        executors.put(IssueVendor.GITLAB, properties.getIssue().getGitlab().isEnabled()
                ? new GitlabIssueVendorExecutor(properties.getIssue().getGitlab(), properties.getIssue().getGitlabExecutorName(), mapper, properties.getExecutionTimeout())
                : new MockCompatibleIssueVendorExecutor(IssueVendor.GITLAB, properties.getIssue().getGitlabExecutorName(), properties.getIssue().isGitlabMockEnabled()));
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
        IssueVendor vendor = vendorResolver.resolve(action);
        if (vendor == null) {
            return AdapterExecutionResult.permanentFailure(name(), "Issue vendor is not configured or is unsupported");
        }
        if (vendor == IssueVendor.MOCK) {
            if (!properties.getMock().isEnabled()) {
                return AdapterExecutionResult.permanentFailure(name(), "MOCK issue vendor is disabled outside explicit local/test/e2e opt-in");
            }
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
        IssueTrackingActionExecutor executor = executors.get(vendor);
        if (executor == null || !enabled(executor)) {
            throw new AdapterExecutorUnavailableException("No enabled issue executor for vendor " + vendor);
        }
        IssueExecutorResponse response = executor.execute(IssueExecutorRequest.from(action, vendor));
        if (response.isSuccess()) {
            AdapterExecutionResult result = AdapterExecutionResult.success(executorName(executor), responseRef(response, action));
            result.setIssueVendor(response.getVendor() == null ? vendor.name() : response.getVendor());
            result.setIssueId(response.getIssueId());
            result.setIssueUrl(response.getIssueUrl());
            result.setIssueStatus(response.getIssueStatus());
            return result;
        }
        if (response.isRetryable()) {
            return AdapterExecutionResult.retryableFailure(executorName(executor), response.getError());
        }
        return AdapterExecutionResult.permanentFailure(executorName(executor), response.getError());
    }

    private boolean enabled(IssueTrackingActionExecutor executor) {
        if (executor instanceof MockCompatibleIssueVendorExecutor mock) return mock.enabled();
        if (executor instanceof RedmineIssueVendorExecutor redmine) return redmine.enabled();
        if (executor instanceof GitlabIssueVendorExecutor gitlab) return gitlab.enabled();
        return true;
    }

    private String executorName(IssueTrackingActionExecutor executor) {
        if (executor instanceof MockCompatibleIssueVendorExecutor mock) return mock.executorName();
        if (executor instanceof RedmineIssueVendorExecutor redmine) return redmine.executorName();
        if (executor instanceof GitlabIssueVendorExecutor gitlab) return gitlab.executorName();
        return name();
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
