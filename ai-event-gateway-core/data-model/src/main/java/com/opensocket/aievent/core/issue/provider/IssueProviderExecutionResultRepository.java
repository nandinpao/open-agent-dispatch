package com.opensocket.aievent.core.issue.provider;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence port for immutable provider evidence and mutable TaskIssueLink projection state. */
public interface IssueProviderExecutionResultRepository {
    IssueProviderExecutionResult saveObserved(IssueProviderExecutionResult value);
    Optional<IssueProviderExecutionResult> findByResultId(String resultId);
    Optional<IssueProviderExecutionResult> findByActionAttempt(String adapterActionId, int attemptNo);
    Optional<IssueProviderExecutionResult> findLatestByAction(String adapterActionId);
    List<IssueProviderExecutionResult> findProjectionDue(OffsetDateTime now, int limit);
    IssueProviderExecutionResult markProjectionSucceeded(String resultId, OffsetDateTime projectedAt);
    IssueProviderExecutionResult markProjectionRetry(String resultId, int attemptCount, OffsetDateTime nextAttemptAt, String error);
    IssueProviderExecutionResult markProjectionFailedPermanent(String resultId, int attemptCount, String error, OffsetDateTime failedAt);
    IssueProviderExecutionResult markProjectionNotRequired(String resultId, String reason, OffsetDateTime resolvedAt);
    String mode();

    static IssueProviderExecutionResultRepository noop() {
        return new IssueProviderExecutionResultRepository() {
            @Override public IssueProviderExecutionResult saveObserved(IssueProviderExecutionResult v) { return v; }
            @Override public Optional<IssueProviderExecutionResult> findByResultId(String id) { return Optional.empty(); }
            @Override public Optional<IssueProviderExecutionResult> findByActionAttempt(String id,int attempt) { return Optional.empty(); }
            @Override public Optional<IssueProviderExecutionResult> findLatestByAction(String id) { return Optional.empty(); }
            @Override public List<IssueProviderExecutionResult> findProjectionDue(OffsetDateTime now,int limit) { return List.of(); }
            @Override public IssueProviderExecutionResult markProjectionSucceeded(String id,OffsetDateTime at) { return null; }
            @Override public IssueProviderExecutionResult markProjectionRetry(String id,int attempts,OffsetDateTime next,String error) { return null; }
            @Override public IssueProviderExecutionResult markProjectionFailedPermanent(String id,int attempts,String error,OffsetDateTime at) { return null; }
            @Override public IssueProviderExecutionResult markProjectionNotRequired(String id,String reason,OffsetDateTime at) { return null; }
            @Override public String mode() { return "NOOP"; }
        };
    }
}
