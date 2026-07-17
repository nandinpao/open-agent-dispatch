package com.opensocket.aievent.core.action.executor.issue;

import java.util.Locale;

public class MockCompatibleIssueVendorExecutor implements IssueTrackingActionExecutor {
    private final IssueVendor vendor;
    private final String executorName;
    private final boolean enabled;

    public MockCompatibleIssueVendorExecutor(IssueVendor vendor, String executorName, boolean enabled) {
        this.vendor = vendor;
        this.executorName = executorName;
        this.enabled = enabled;
    }

    @Override
    public IssueVendor vendor() { return vendor; }

    public String executorName() { return executorName; }

    public boolean enabled() { return enabled; }

    @Override
    public IssueExecutorResponse execute(IssueExecutorRequest request) {
        IssueExecutorResponse response = new IssueExecutorResponse();
        if (!enabled) {
            response.setSuccess(false);
            response.setError(vendor.name() + " mock-compatible executor is disabled");
            return response;
        }
        response.setSuccess(true);
        String prefix = vendor.name().toLowerCase(Locale.ROOT);
        response.setVendor(vendor.name());
        response.setIssueId(prefix + "-mock-issue-" + request.getIncidentId());
        response.setIssueStatus("mock_synced");
        response.setIssueUrl("mock://" + prefix + "/issues/" + response.getIssueId());
        response.setResponseRef(prefix + "-mock-response:" + request.getActionId());
        return response;
    }
}
