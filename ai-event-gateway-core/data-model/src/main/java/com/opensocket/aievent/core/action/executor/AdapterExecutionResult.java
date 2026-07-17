package com.opensocket.aievent.core.action.executor;

public class AdapterExecutionResult {
    private AdapterExecutionOutcome outcome = AdapterExecutionOutcome.SUCCESS;
    private String executorName;
    private String responseRef;
    private String error;
    private boolean retryable;
    private String issueVendor;
    private String issueId;
    private String issueUrl;
    private String issueStatus;

    public static AdapterExecutionResult success(String executorName, String responseRef) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.SUCCESS);
        result.setExecutorName(executorName);
        result.setResponseRef(responseRef);
        result.setRetryable(false);
        return result;
    }

    public static AdapterExecutionResult failed(String executorName, String error) {
        return retryableFailure(executorName, error);
    }

    public static AdapterExecutionResult retryableFailure(String executorName, String error) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.RETRYABLE_FAILURE);
        result.setExecutorName(executorName);
        result.setError(error);
        result.setRetryable(true);
        return result;
    }

    public static AdapterExecutionResult permanentFailure(String executorName, String error) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.PERMANENT_FAILURE);
        result.setExecutorName(executorName);
        result.setError(error);
        result.setRetryable(false);
        return result;
    }

    public static AdapterExecutionResult executorUnavailable(String executorName, String error) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.EXECUTOR_UNAVAILABLE);
        result.setExecutorName(executorName);
        result.setError(error);
        result.setRetryable(true);
        return result;
    }

    public static AdapterExecutionResult timeout(String executorName, String error) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.TIMEOUT);
        result.setExecutorName(executorName);
        result.setError(error);
        result.setRetryable(true);
        return result;
    }

    public boolean isSuccess() { return outcome == AdapterExecutionOutcome.SUCCESS; }
    public AdapterExecutionOutcome getOutcome() { return outcome; }
    public void setOutcome(AdapterExecutionOutcome outcome) { this.outcome = outcome == null ? AdapterExecutionOutcome.RETRYABLE_FAILURE : outcome; }
    public String getExecutorName() { return executorName; }
    public void setExecutorName(String executorName) { this.executorName = executorName; }
    public String getResponseRef() { return responseRef; }
    public void setResponseRef(String responseRef) { this.responseRef = responseRef; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public boolean isRetryable() { return retryable || outcome == AdapterExecutionOutcome.RETRYABLE_FAILURE || outcome == AdapterExecutionOutcome.EXECUTOR_UNAVAILABLE || outcome == AdapterExecutionOutcome.TIMEOUT; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }
    public String getIssueVendor() { return issueVendor; }
    public void setIssueVendor(String issueVendor) { this.issueVendor = issueVendor; }
    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }
    public String getIssueUrl() { return issueUrl; }
    public void setIssueUrl(String issueUrl) { this.issueUrl = issueUrl; }
    public String getIssueStatus() { return issueStatus; }
    public void setIssueStatus(String issueStatus) { this.issueStatus = issueStatus; }
}
