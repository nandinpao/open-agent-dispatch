package com.opensocket.aievent.core.enforce;

import java.util.List;

public interface EnforceOperationsRepository {
    EnforceObservabilitySnapshot observabilitySnapshot();
    List<EnforceRoutingAuditRecord> searchRoutingAudit(String taskId, String agentId, String blockingCode,
                                                        String policyCode, String window, int limit);
    EnforceOperatorIncidentResult createOperatorIncident(EnforceOperatorIncidentRequest request, String actor);
    List<EnforceLegacyFinalReportItem> legacyFinalReport();
    List<EnforceArtifactRetentionRecord> artifactRetention();
}
