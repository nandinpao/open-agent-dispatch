package com.opensocket.aievent.database.persistence.eventprocessing.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class EventDecisionPo {
    private String eventId; private String fingerprint; private String incidentId; private String decisionType; private boolean duplicate;
    private long occurrenceCount; private String actionsJson; private String reason; private OffsetDateTime decidedAt;
}
