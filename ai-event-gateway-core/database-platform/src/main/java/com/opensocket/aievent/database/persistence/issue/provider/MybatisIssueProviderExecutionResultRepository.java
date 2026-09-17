package com.opensocket.aievent.database.persistence.issue.provider;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.opensocket.aievent.core.issue.provider.IssueProviderExecutionResult;
import com.opensocket.aievent.core.issue.provider.IssueProviderExecutionResultRepository;
import com.opensocket.aievent.database.persistence.issue.provider.dao.IssueProviderExecutionResultDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class MybatisIssueProviderExecutionResultRepository implements IssueProviderExecutionResultRepository {
    private final IssueProviderExecutionResultDao dao;
    public MybatisIssueProviderExecutionResultRepository(IssueProviderExecutionResultDao dao) { this.dao = dao; }

    @Override public IssueProviderExecutionResult saveObserved(IssueProviderExecutionResult value) {
        if (value == null || blank(value.getResultId()) || blank(value.getAdapterActionId())) {
            throw new IllegalArgumentException("issue provider execution result identity is required");
        }
        dao.insertObserved(value);
        IssueProviderExecutionResult stored = dao.findByResultId(value.getResultId());
        if (stored == null) {
            stored = dao.findByActionAttempt(value.getAdapterActionId(), value.getAttemptNo());
        }
        if (stored == null) throw new IllegalStateException("ISSUE_PROVIDER_RESULT_PERSISTED_ROW_NOT_FOUND:" + value.getResultId());
        if (value.getResponseFingerprint() != null && stored.getResponseFingerprint() != null
                && !value.getResponseFingerprint().equals(stored.getResponseFingerprint())) {
            throw new IllegalStateException("ISSUE_PROVIDER_RESULT_CONFLICT: immutable provider evidence differs for action="
                    + value.getAdapterActionId() + " attempt=" + value.getAttemptNo() + " observation=" + value.getObservationKind());
        }
        return stored;
    }
    @Override public Optional<IssueProviderExecutionResult> findByResultId(String resultId) { return blank(resultId)?Optional.empty():Optional.ofNullable(dao.findByResultId(resultId)); }
    @Override public Optional<IssueProviderExecutionResult> findByActionAttempt(String adapterActionId,int attemptNo) { return blank(adapterActionId)?Optional.empty():Optional.ofNullable(dao.findByActionAttempt(adapterActionId,Math.max(1,attemptNo))); }
    @Override public Optional<IssueProviderExecutionResult> findLatestByAction(String adapterActionId) { return blank(adapterActionId)?Optional.empty():Optional.ofNullable(dao.findLatestByAction(adapterActionId)); }
    @Override public List<IssueProviderExecutionResult> findProjectionDue(OffsetDateTime now,int limit) { return dao.findProjectionDue(now,Math.max(1,Math.min(limit,1000))); }
    @Override public IssueProviderExecutionResult markProjectionSucceeded(String resultId,OffsetDateTime projectedAt) { dao.markProjectionSucceeded(resultId,projectedAt); return required(resultId); }
    @Override public IssueProviderExecutionResult markProjectionRetry(String resultId,int attemptCount,OffsetDateTime nextAttemptAt,String error) { dao.markProjectionRetry(resultId,Math.max(1,attemptCount),nextAttemptAt,error); return required(resultId); }
    @Override public IssueProviderExecutionResult markProjectionFailedPermanent(String resultId,int attemptCount,String error,OffsetDateTime failedAt) { dao.markProjectionFailedPermanent(resultId,Math.max(1,attemptCount),error,failedAt); return required(resultId); }
    @Override public IssueProviderExecutionResult markProjectionNotRequired(String resultId,String reason,OffsetDateTime resolvedAt) { dao.markProjectionNotRequired(resultId,reason,resolvedAt); return required(resultId); }
    @Override public String mode() { return "MYBATIS"; }
    private IssueProviderExecutionResult required(String resultId) { return findByResultId(resultId).orElseThrow(() -> new IllegalStateException("ISSUE_PROVIDER_RESULT_NOT_FOUND:"+resultId)); }
    private boolean blank(String v) { return v==null||v.isBlank(); }
}
