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
    private String executorName; private String beforeStatus; private String afterStatus; private String outcome; private String message; private int attemptCount;
    private OffsetDateTime createdAt; private String payloadSnapshotJson;
}
