package com.opensocket.aievent.core.action.executor.issue;

public interface IssueTrackingActionExecutor {
    IssueVendor vendor();
    IssueExecutorResponse execute(IssueExecutorRequest request);
}
