package com.opensocket.aievent.database.persistence.execution.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.execution.po.DispatchRequestPo;

@Mapper
public interface DispatchRequestDao {
    int upsert(@Param("request") DispatchRequestPo request);
    DispatchRequestPo findById(@Param("dispatchRequestId") String dispatchRequestId);
    DispatchRequestPo findOpenByAssignmentId(@Param("assignmentId") String assignmentId);
    List<DispatchRequestPo> findByTaskId(@Param("taskId") String taskId, @Param("limit") int limit);
    List<DispatchRequestPo> findByStatus(@Param("status") String status, @Param("limit") int limit);
    DispatchRequestPo claimById(
            @Param("dispatchRequestId") String dispatchRequestId,
            @Param("workerId") String workerId,
            @Param("now") OffsetDateTime now,
            @Param("claimUntil") OffsetDateTime claimUntil);
    List<DispatchRequestPo> claimExecutable(
            @Param("workerId") String workerId,
            @Param("now") OffsetDateTime now,
            @Param("claimUntil") OffsetDateTime claimUntil,
            @Param("limit") int limit);
    int saveClaimed(
            @Param("request") DispatchRequestPo request,
            @Param("workerId") String workerId,
            @Param("expectedClaimUntil") OffsetDateTime expectedClaimUntil);
    int transitionStatus(
            @Param("dispatchRequestId") String dispatchRequestId,
            @Param("allowedStatuses") List<String> allowedStatuses,
            @Param("newStatus") String newStatus,
            @Param("expectedAttemptNo") Integer expectedAttemptNo,
            @Param("expectedDispatchToken") String expectedDispatchToken,
            @Param("lastCallbackId") String lastCallbackId,
            @Param("reason") String reason,
            @Param("lastError") String lastError,
            @Param("completedAt") OffsetDateTime completedAt,
            @Param("failedAt") OffsetDateTime failedAt,
            @Param("timedOutAt") OffsetDateTime timedOutAt,
            @Param("retryWaitingAt") OffsetDateTime retryWaitingAt,
            @Param("nextRetryAt") OffsetDateTime nextRetryAt,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("clearClaim") boolean clearClaim);
    List<DispatchRequestPo> recent(@Param("limit") int limit);
}
