package com.opensocket.aievent.database.persistence.incident.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentQuery;
import com.opensocket.aievent.core.incident.IncidentRepository;
import com.opensocket.aievent.core.incident.IncidentStatus;
import com.opensocket.aievent.database.persistence.incident.dao.IncidentDao;
import com.opensocket.aievent.database.persistence.incident.po.IncidentPo;
import com.opensocket.aievent.database.persistence.incident.converter.IncidentPersistenceConverter;


@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "incident", name = "store", havingValue = "MYBATIS")
public class MybatisIncidentRepository implements IncidentRepository {
    private final IncidentDao dao;
    private final IncidentPersistenceConverter converter;

    public MybatisIncidentRepository(IncidentDao dao, IncidentPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
        public Incident save(Incident incident) {
            dao.upsert(converter.toPo(incident));
            return incident;
        }

    @Override
        public Incident saveNewOrGetActive(Incident incident) {
            try {
                dao.insert(converter.toPo(incident));
                return incident;
            } catch (DuplicateKeyException ex) {
                return findActiveByFingerprint(incident.getFingerprint()).orElseThrow(() -> ex);
            }
        }

    @Override
        public Optional<Incident> findById(String incidentId) {
            return Optional.ofNullable(dao.findById(incidentId)).map(converter::toIncident);
        }

    @Override
        public Optional<Incident> findActiveByFingerprint(String fingerprint) {
            return Optional.ofNullable(dao.findActiveByFingerprint(fingerprint)).map(converter::toIncident);
        }

    @Override
        public Optional<Incident> findLatestByFingerprint(String fingerprint) {
            return Optional.ofNullable(dao.findLatestByFingerprint(fingerprint)).map(converter::toIncident);
        }

    @Override
        public List<Incident> findActiveLastSeenBefore(OffsetDateTime cutoff, int limit) {
            return dao.findActiveLastSeenBefore(cutoff, Math.max(1, Math.min(limit, 1000))).stream().map(converter::toIncident).toList();
        }

    @Override
        public List<Incident> findAll() {
            return dao.findAll().stream().map(converter::toIncident).toList();
        }

    @Override
        public List<Incident> search(IncidentQuery query) {
            return dao.search(query.getTenantId(), query.getSourceSystem(), query.getSiteId(), query.getPlantId(), query.getObjectType(), query.getObjectId(), query.getEventType(), query.getErrorCode(),
                    query.getSeverity() == null ? null : query.getSeverity().name(), query.getStatus() == null ? null : query.getStatus().name(), query.getLimit())
                    .stream().map(converter::toIncident).toList();
        }

    @Override
        public String mode() { return "MYBATIS"; }
}
