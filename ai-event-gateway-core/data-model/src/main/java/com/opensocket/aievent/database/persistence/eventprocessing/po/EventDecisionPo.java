package com.opensocket.aievent.database.persistence.eventprocessing.po;

import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter; import java.time.OffsetDateTime;
@Getter @Setter @NoArgsConstructor
public class EventDecisionPo {
    private String eventId; private String tenantId; private String sourceSystem; private String eventType; private String eventStage;
    private String correlationId; private String normalizedMessage; private String payloadJson; private OffsetDateTime occurredAt;
    private String fingerprint; private String incidentId; private String decisionType; private boolean duplicate; private long occurrenceCount;
    private String actionsJson; private String reason; private OffsetDateTime decidedAt; private String ownerDepartmentId; private String ownerGroupId;
    private String scopeStatus; private Long scopeSourceVersion; private OffsetDateTime scopeInheritedAt;
}
