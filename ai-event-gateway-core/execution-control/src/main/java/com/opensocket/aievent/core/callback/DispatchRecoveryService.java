package com.opensocket.aievent.core.callback;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.DispatchStatusTransition;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;
import com.opensocket.aievent.core.task.TaskExecutionStateTransition;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskStatus;

@Service
public class DispatchRecoveryService {
    private final DispatchRequestRepository dispatchRepository;
    private final TaskOrchestrationFacade taskOrchestrationFacade;
    private final TaskCallbackProperties properties;

    public DispatchRecoveryService(DispatchRequestRepository dispatchRepository,
                                   TaskOrchestrationFacade taskOrchestrationFacade,
                                   TaskCallbackProperties properties) {
        this.dispatchRepository = dispatchRepository;
        this.taskOrchestrationFacade = taskOrchestrationFacade;
        this.properties = properties;
    }

    @Transactional
    public List<DispatchRecoveryResult> scanAndRecoverTimedOut(int limit) {
        if (!properties.getRecovery().isTimeoutEnabled()) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(limit, properties.getRecovery().getMaxBatchSize()));
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getRecovery().getDispatchTimeout());
        List<DispatchRequest> candidates = new ArrayList<>();
        candidates.addAll(dispatchRepository.findByStatus(DispatchRequestStatus.DISPATCHED, capped));
        candidates.addAll(dispatchRepository.findByStatus(DispatchRequestStatus.ACKED, capped));
        candidates.addAll(dispatchRepository.findByStatus(DispatchRequestStatus.RUNNING, capped));
        return candidates.stream()
                .filter(request -> isTimedOut(request, cutoff))
                .limit(capped)
                .map(this::markTimedOut)
                .toList();
    }

    @Transactional
    public DispatchRecoveryResult markTimedOut(String dispatchRequestId) {
        DispatchRequest request = dispatchRepository.findById(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        return markTimedOut(request);
    }

    private DispatchRecoveryResult markTimedOut(DispatchRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DispatchRecoveryResult result = new DispatchRecoveryResult();
        result.setDispatchRequestId(request.getDispatchRequestId());
        result.setTaskId(request.getTaskId());
        result.setPreviousDispatchStatus(request.getStatus() == null ? null : request.getStatus().name());
        result.setTimedOut(true);

        boolean retry = properties.getRecovery().isRetryEnabled()
                && request.getAttemptCount() < properties.getRecovery().getMaxAttempts();
        Duration backoff = retry ? computeBackoff(request.getAttemptCount() + 1, request.getDispatchRequestId()) : Duration.ZERO;
        DispatchRequestStatus targetStatus = retry ? DispatchRequestStatus.RETRY_WAITING : DispatchRequestStatus.TIMED_OUT;

        DispatchStatusTransition transition = new DispatchStatusTransition();
        transition.setDispatchRequestId(request.getDispatchRequestId());
        transition.setAllowedCurrentStatuses(List.of(
                DispatchRequestStatus.DISPATCHED,
                DispatchRequestStatus.ACKED,
                DispatchRequestStatus.RUNNING));
        transition.setNewStatus(targetStatus);
        transition.setExpectedAttemptNo(request.getAttemptCount());
        transition.setExpectedDispatchToken(request.getDispatchToken());
        transition.setUpdatedAt(now);
        transition.setFailedAt(now);
        transition.setTimedOutAt(now);
        if (retry) {
            transition.setReason("Dispatch timed out; retry scheduled at " + now.plus(backoff) + ". attemptCount=" + request.getAttemptCount());
            transition.setLastError("Dispatch timed out; retry scheduled");
            transition.setRetryWaitingAt(now);
            transition.setNextRetryAt(now.plus(backoff));
        } else {
            transition.setReason("Dispatch timed out and no retry is scheduled");
            transition.setLastError("Dispatch timed out after " + properties.getRecovery().getDispatchTimeout());
        }

        PersistenceWriteResult write = dispatchRepository.transitionStatus(transition);
        if (!write.applied()) {
            DispatchRequest latest = dispatchRepository.findById(request.getDispatchRequestId()).orElse(request);
            result.setTimedOut(false);
            result.setRetryScheduled(false);
            result.setNewDispatchStatus(latest.getStatus() == null ? null : latest.getStatus().name());
            result.setMessage("Timed-out recovery skipped because dispatch state changed concurrently");
            return result;
        }

        DispatchRequest updated = dispatchRepository.findById(request.getDispatchRequestId()).orElse(request);
        result.setNewDispatchStatus(updated.getStatus() == null ? null : updated.getStatus().name());
        result.setRetryScheduled(retry);
        result.setMessage(retry ? "Timed out and moved to RETRY_WAITING" : "Timed out and marked TIMED_OUT");

        taskOrchestrationFacade.findTask(request.getTaskId()).ifPresent(task -> {
            result.setPreviousTaskStatus(task.getStatus() == null ? null : task.getStatus().name());
            TaskExecutionStateTransition taskTransition = new TaskExecutionStateTransition();
            taskTransition.setTaskId(task.getTaskId());
            taskTransition.setAllowedCurrentStatuses(List.of(TaskStatus.ASSIGNED, TaskStatus.DISPATCHED, TaskStatus.RUNNING, TaskStatus.RECONCILING));
            taskTransition.setUpdatedAt(now);
            if (retry) {
                taskTransition.setNewStatus(TaskStatus.DISPATCHED);
                taskTransition.setLifecycleReason("Dispatch timeout recovery scheduled retry");
            } else if (properties.getRecovery().isAutoFailTimedOut()) {
                taskTransition.setNewStatus(TaskStatus.ORPHANED);
                taskTransition.setTimeoutAt(now);
                taskTransition.setTerminalAt(now);
                taskTransition.setLifecycleReason("Dispatch timed out after recovery timeout");
            } else {
                result.setNewTaskStatus(task.getStatus() == null ? null : task.getStatus().name());
                return;
            }
            taskOrchestrationFacade.transitionExecutionState(taskTransition);
            taskOrchestrationFacade.findTask(task.getTaskId())
                    .ifPresent(latest -> result.setNewTaskStatus(latest.getStatus() == null ? null : latest.getStatus().name()));
        });
        return result;
    }

    private Duration computeBackoff(int nextAttemptNo, String stableJitterKey) {
        long multiplier = 1L << Math.max(0, Math.min(nextAttemptNo - 1, 10));
        Duration initial = properties.getRecovery().getInitialBackoff();
        Duration max = properties.getRecovery().getMaxBackoff();
        Duration candidate = initial.multipliedBy(multiplier);
        Duration capped = candidate.compareTo(max) > 0 ? max : candidate;
        return applyDeterministicJitter(capped, stableJitterKey, properties.getRecovery().getJitterPercent());
    }

    private Duration applyDeterministicJitter(Duration base, String stableJitterKey, int jitterPercent) {
        if (base == null || base.isZero() || base.isNegative() || jitterPercent <= 0 || stableJitterKey == null || stableJitterKey.isBlank()) {
            return base == null ? Duration.ZERO : base;
        }
        long millis = Math.max(1L, base.toMillis());
        long spread = Math.max(1L, millis * Math.min(jitterPercent, 100) / 100L);
        int bucket = Math.floorMod(stableJitterKey.hashCode(), 201) - 100;
        long delta = spread * bucket / 100L;
        return Duration.ofMillis(Math.max(1L, millis + delta));
    }

    private boolean isTimedOut(DispatchRequest request, OffsetDateTime cutoff) {
        OffsetDateTime marker = request.getUpdatedAt();
        if (request.getDispatchedAt() != null) {
            marker = request.getDispatchedAt();
        }
        return marker != null && marker.isBefore(cutoff);
    }
}
