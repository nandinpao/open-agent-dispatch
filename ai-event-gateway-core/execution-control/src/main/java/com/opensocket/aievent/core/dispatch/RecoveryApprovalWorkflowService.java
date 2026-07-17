package com.opensocket.aievent.core.dispatch;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application service boundary for P10.7 recovery approval workflow persistence.
 *
 * <p>The inbound API layer must not import repository ports directly. This service keeps
 * approval request storage inside the execution-control context while preserving the existing
 * controller contract.</p>
 */
@Service
public class RecoveryApprovalWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(RecoveryApprovalWorkflowService.class);
    private final AtomicBoolean missingSchemaWarningLogged = new AtomicBoolean(false);
    private final RecoveryApprovalRepository repository;

    public RecoveryApprovalWorkflowService(RecoveryApprovalRepository repository) {
        this.repository = repository;
    }

    public RecoveryApprovalRequest save(RecoveryApprovalRequest request) {
        return repository.save(request);
    }

    public Optional<RecoveryApprovalRequest> findById(String approvalId) {
        try {
            return repository.findById(approvalId);
        } catch (RuntimeException ex) {
            if (isMissingApprovalSchema(ex)) {
                warnMissingSchemaOnce(ex);
                return Optional.empty();
            }
            throw ex;
        }
    }

    public List<RecoveryApprovalRequest> findByStatus(RecoveryApprovalStatus status, int limit) {
        try {
            return repository.findByStatus(status, limit);
        } catch (RuntimeException ex) {
            if (isMissingApprovalSchema(ex)) {
                warnMissingSchemaOnce(ex);
                return List.of();
            }
            throw ex;
        }
    }

    public List<RecoveryApprovalRequest> recent(int limit) {
        try {
            return repository.recent(limit);
        } catch (RuntimeException ex) {
            if (isMissingApprovalSchema(ex)) {
                warnMissingSchemaOnce(ex);
                return List.of();
            }
            throw ex;
        }
    }

    public String mode() {
        return repository.mode();
    }

    private boolean isMissingApprovalSchema(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("does not exist")
                        && (normalized.contains("recovery_approval_requests")
                        || normalized.contains("idx_recovery_approval"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void warnMissingSchemaOnce(Throwable exception) {
        if (missingSchemaWarningLogged.compareAndSet(false, true)) {
            log.warn("Recovery approval schema is not ready; approval queue queries will temporarily return empty results. Run Flyway V37/V39 or manually create recovery_approval_requests. Root cause: {}", rootMessage(exception));
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? "unknown" : (current.getMessage() == null ? current.getClass().getName() : current.getMessage());
    }
}
