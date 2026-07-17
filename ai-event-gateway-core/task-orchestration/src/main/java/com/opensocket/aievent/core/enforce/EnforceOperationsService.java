package com.opensocket.aievent.core.enforce;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnforceOperationsService {
    private final EnforceOperationsRepository repository;

    public EnforceOperationsService(EnforceOperationsRepository repository) {
        this.repository = repository;
    }

    public EnforceObservabilitySnapshot observabilitySnapshot() {
        return repository.observabilitySnapshot();
    }

    public List<EnforceRoutingAuditRecord> searchRoutingAudit(String taskId, String agentId,
            String blockingCode, String policyCode, String window, int limit) {
        return repository.searchRoutingAudit(blankToNull(taskId), blankToNull(agentId), normalizeNullable(blockingCode),
                normalizeNullable(policyCode), blankToNull(window), bounded(limit, 500));
    }

    @Transactional
    public EnforceOperatorIncidentResult createOperatorIncident(EnforceOperatorIncidentRequest request, String actor) {
        if (request == null) throw new IllegalArgumentException("incident request is required");
        if (blank(request.getTriggerCode())) throw new IllegalArgumentException("triggerCode is required");
        if (blank(request.getMessage())) throw new IllegalArgumentException("message is required");
        request.setTriggerCode(normalize(request.getTriggerCode()));
        request.setSeverity(normalize(firstNonBlank(request.getSeverity(), "HIGH")));
        return repository.createOperatorIncident(request, firstNonBlank(actor, "SYSTEM_OPERATOR"));
    }

    public List<EnforceLegacyFinalReportItem> legacyFinalReport() { return repository.legacyFinalReport(); }
    public List<EnforceArtifactRetentionRecord> artifactRetention() { return repository.artifactRetention(); }

    private static int bounded(int value, int max) { return Math.max(1, Math.min(value <= 0 ? 100 : value, max)); }
    private static String normalizeNullable(String value) { return blank(value) ? null : normalize(value); }
    private static String normalize(String value) { return value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT); }
    private static String blankToNull(String value) { return blank(value) ? null : value.trim(); }
    private static String firstNonBlank(String first, String fallback) { return blank(first) ? fallback : first; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
