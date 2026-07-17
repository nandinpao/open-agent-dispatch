package com.opensocket.aievent.core.dispatch;

import java.util.List;
import java.util.Optional;

public interface RecoveryApprovalRepository {
    RecoveryApprovalRequest save(RecoveryApprovalRequest request);
    Optional<RecoveryApprovalRequest> findById(String approvalId);
    List<RecoveryApprovalRequest> findByStatus(RecoveryApprovalStatus status, int limit);
    List<RecoveryApprovalRequest> recent(int limit);
    String mode();
}
