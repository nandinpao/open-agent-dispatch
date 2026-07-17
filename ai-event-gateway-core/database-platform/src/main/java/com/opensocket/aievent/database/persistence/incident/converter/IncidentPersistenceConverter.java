package com.opensocket.aievent.database.persistence.incident.converter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentQuery;
import com.opensocket.aievent.core.incident.IncidentStatus;
import com.opensocket.aievent.database.persistence.incident.po.IncidentPo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "incident", name = "store", havingValue = "MYBATIS")
public class IncidentPersistenceConverter {


    public IncidentPo toPo(Incident incident) {
            IncidentPo po = new IncidentPo();
            po.setIncidentId(incident.getIncidentId());
            po.setFingerprint(incident.getFingerprint());
            po.setTenantId(incident.getTenantId());
            po.setSourceSystem(incident.getSourceSystem());
            po.setSiteId(incident.getSiteId());
            po.setPlantId(incident.getPlantId());
            po.setObjectType(incident.getObjectType());
            po.setObjectId(incident.getObjectId());
            po.setEventType(incident.getEventType());
            po.setErrorCode(incident.getErrorCode());
            po.setSeverity(incident.getSeverity() == null ? null : incident.getSeverity().name());
            po.setStatus(incident.getStatus() == null ? null : incident.getStatus().name());
            po.setFirstSeenAt(incident.getFirstSeenAt());
            po.setLastSeenAt(incident.getLastSeenAt());
            po.setOccurrenceCount(incident.getOccurrenceCount());
            po.setLastMessage(incident.getLastMessage());
            po.setLinkedTaskId(incident.getLinkedTaskId());
            po.setLinkedIssueId(incident.getLinkedIssueId());
            po.setResolvedAt(incident.getResolvedAt());
            po.setReopenedAt(incident.getReopenedAt());
            po.setReopenCount(incident.getReopenCount());
            po.setLifecycleReason(incident.getLifecycleReason());
            return po;
        }

    public Incident toIncident(IncidentPo po) {
            Incident incident = new Incident();
            incident.setIncidentId(po.getIncidentId());
            incident.setFingerprint(po.getFingerprint());
            incident.setTenantId(po.getTenantId());
            incident.setSourceSystem(po.getSourceSystem());
            incident.setSiteId(po.getSiteId());
            incident.setPlantId(po.getPlantId());
            incident.setObjectType(po.getObjectType());
            incident.setObjectId(po.getObjectId());
            incident.setEventType(po.getEventType());
            incident.setErrorCode(po.getErrorCode());
            incident.setSeverity(EventSeverity.parse(po.getSeverity()));
            incident.setStatus(po.getStatus() == null ? null : IncidentStatus.valueOf(po.getStatus()));
            incident.setFirstSeenAt(po.getFirstSeenAt());
            incident.setLastSeenAt(po.getLastSeenAt());
            incident.setOccurrenceCount(po.getOccurrenceCount());
            incident.setLastMessage(po.getLastMessage());
            incident.setLinkedTaskId(po.getLinkedTaskId());
            incident.setLinkedIssueId(po.getLinkedIssueId());
            incident.setResolvedAt(po.getResolvedAt());
            incident.setReopenedAt(po.getReopenedAt());
            incident.setReopenCount(po.getReopenCount());
            incident.setLifecycleReason(po.getLifecycleReason());
            return incident;
        }
}
