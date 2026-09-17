package com.opensocket.aievent.database.persistence.issue.provider.dao;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.core.issue.provider.IssueProviderExecutionResult;

@Mapper
public interface IssueProviderExecutionResultDao {
    int insertObserved(@Param("value") IssueProviderExecutionResult value);
    IssueProviderExecutionResult findByResultId(@Param("resultId") String resultId);
    IssueProviderExecutionResult findByActionAttempt(@Param("adapterActionId") String adapterActionId,@Param("attemptNo") int attemptNo);
    IssueProviderExecutionResult findLatestByAction(@Param("adapterActionId") String adapterActionId);
    List<IssueProviderExecutionResult> findProjectionDue(@Param("now") OffsetDateTime now,@Param("limit") int limit);
    int markProjectionSucceeded(@Param("resultId") String resultId,@Param("projectedAt") OffsetDateTime projectedAt);
    int markProjectionRetry(@Param("resultId") String resultId,@Param("attemptCount") int attemptCount,@Param("nextAttemptAt") OffsetDateTime nextAttemptAt,@Param("error") String error);
    int markProjectionFailedPermanent(@Param("resultId") String resultId,@Param("attemptCount") int attemptCount,@Param("error") String error,@Param("failedAt") OffsetDateTime failedAt);
    int markProjectionNotRequired(@Param("resultId") String resultId,@Param("reason") String reason,@Param("resolvedAt") OffsetDateTime resolvedAt);
}
