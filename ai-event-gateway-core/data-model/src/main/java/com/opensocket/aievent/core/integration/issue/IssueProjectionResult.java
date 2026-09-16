package com.opensocket.aievent.core.integration.issue;
import com.opensocket.aievent.core.issue.TaskIssueLink;
public record IssueProjectionResult(TaskIssueLink link,IntegrationOutboxEntry outbox,boolean idempotentReplay) {}
