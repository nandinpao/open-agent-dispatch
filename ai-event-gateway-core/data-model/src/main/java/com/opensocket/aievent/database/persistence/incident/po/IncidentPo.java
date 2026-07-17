package com.opensocket.aievent.database.persistence.incident.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class IncidentPo {
    private String incidentId;
    private String fingerprint;
    private String tenantId;
    private String sourceSystem;
    private String siteId;
    private String plantId;
    private String objectType;
    private String objectId;
    private String eventType;
    private String errorCode;
    private String severity;
    private String status;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;
    private long occurrenceCount;
    private String lastMessage;
    private String linkedTaskId;
    private String linkedIssueId;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime reopenedAt;
    private int reopenCount;
    private String lifecycleReason;
    public void setReopenCount(int reopenCount) { this.reopenCount = Math.max(0, reopenCount); }
}
