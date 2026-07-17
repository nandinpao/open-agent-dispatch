package com.opensocket.aievent.database.persistence.execution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.DispatchStatusTransition;
import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;
import com.opensocket.aievent.database.persistence.execution.converter.DispatchRequestPersistenceConverter;
import com.opensocket.aievent.database.persistence.execution.dao.DispatchRequestDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "dispatch", name = "request-store", havingValue = "MYBATIS")
public class MybatisDispatchRequestRepository implements DispatchRequestRepository {
    private final DispatchRequestDao dao;
    private final DispatchRequestPersistenceConverter converter;

    public MybatisDispatchRequestRepository(
            DispatchRequestDao dao,
            DispatchRequestPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public DispatchRequest save(DispatchRequest request) {
        dao.upsert(converter.toPo(request));
        return request;
    }

    @Override
    public Optional<DispatchRequest> findById(String dispatchRequestId) {
        return Optional.ofNullable(dao.findById(dispatchRequestId)).map(converter::toRequest);
    }

    @Override
    public Optional<DispatchRequest> findOpenByAssignmentId(String assignmentId) {
        return Optional.ofNullable(dao.findOpenByAssignmentId(assignmentId)).map(converter::toRequest);
    }

    @Override
    public List<DispatchRequest> findByTaskId(String taskId, int limit) {
        return dao.findByTaskId(taskId, cap(limit)).stream().map(converter::toRequest).toList();
    }

    @Override
    public List<DispatchRequest> findByStatus(DispatchRequestStatus status, int limit) {
        return dao.findByStatus(status.name(), cap(limit)).stream().map(converter::toRequest).toList();
    }

    @Override
    public Optional<DispatchRequest> claimById(String dispatchRequestId, ClaimRequest request) {
        return Optional.ofNullable(dao.claimById(
                        dispatchRequestId,
                        request.workerId(),
                        request.now(),
                        request.claimUntil()))
                .map(converter::toRequest);
    }

    @Override
    public List<DispatchRequest> claimExecutable(ClaimRequest request) {
        return dao.claimExecutable(
                        request.workerId(),
                        request.now(),
                        request.claimUntil(),
                        request.limit())
                .stream()
                .map(converter::toRequest)
                .toList();
    }

    @Override
    public PersistenceWriteResult saveClaimed(
            DispatchRequest request,
            ClaimOwnership ownership) {
        int rows = dao.saveClaimed(
                converter.toPo(request),
                ownership.workerId(),
                ownership.claimUntil());
        return rows > 0
                ? PersistenceWriteResult.applied(request.getDispatchRequestId(), rows)
                : PersistenceWriteResult.ownershipLost(request.getDispatchRequestId());
    }

    @Override
    public PersistenceWriteResult transitionStatus(DispatchStatusTransition transition) {
        int rows = dao.transitionStatus(
                transition.getDispatchRequestId(),
                transition.allowedCurrentStatusNames(),
                transition.newStatusName(),
                transition.getExpectedAttemptNo(),
                transition.getExpectedDispatchToken(),
                transition.getLastCallbackId(),
                transition.getReason(),
                transition.getLastError(),
                transition.getCompletedAt(),
                transition.getFailedAt(),
                transition.getTimedOutAt(),
                transition.getRetryWaitingAt(),
                transition.getNextRetryAt(),
                transition.getUpdatedAt(),
                transition.isClearClaim());
        return rows > 0
                ? PersistenceWriteResult.applied(transition.getDispatchRequestId(), rows)
                : PersistenceWriteResult.conflict(transition.getDispatchRequestId());
    }

    @Override
    public List<DispatchRequest> recent(int limit) {
        return dao.recent(cap(limit)).stream().map(converter::toRequest).toList();
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit, 1000));
    }
}
