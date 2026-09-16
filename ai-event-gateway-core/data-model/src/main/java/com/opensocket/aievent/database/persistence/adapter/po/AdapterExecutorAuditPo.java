package com.opensocket.aievent.database.persistence.adapter.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AdapterExecutorAuditPo {
    private String auditId; private String actionId; private String taskId; private String incidentId; private String adapterType; private String actionType;
    private String tenantId; private String correlationId; private String a2aRequestId; private String dispatchRequestId; private String assignmentId; private String agentId; private String sourceSystemId; private String connectionId; private String projectMappingId; private String externalProjectId; private String externalIssueId; private Integer providerStatusCode; private String providerFailureCode; private String providerHealthImpact; private String providerOutcomeCertainty; private String idempotencyKey; private String operationFingerprint; private String technicalPrincipalId; private String credentialId; private String credentialVersion;
    private String executorName; private String beforeStatus; private String afterStatus; private String outcome; private String message; private int attemptCount;
    private OffsetDateTime createdAt; private String payloadSnapshotJson;
}
