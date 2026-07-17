package com.opensocket.aievent.database.persistence.incident.repository;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.summary.IncidentOccurrenceSummary;
import com.opensocket.aievent.core.summary.IncidentOccurrenceSummaryRepository;
import com.opensocket.aievent.database.persistence.incident.converter.IncidentOccurrenceSummaryPersistenceConverter;
import com.opensocket.aievent.database.persistence.incident.dao.IncidentOccurrenceSummaryDao;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "incident.summary", name = "store", havingValue = "MYBATIS")
public class MybatisIncidentOccurrenceSummaryRepository implements IncidentOccurrenceSummaryRepository {
    private final IncidentOccurrenceSummaryDao dao;
    private final IncidentOccurrenceSummaryPersistenceConverter converter;

    public MybatisIncidentOccurrenceSummaryRepository(
            IncidentOccurrenceSummaryDao dao,
            IncidentOccurrenceSummaryPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public void recordOccurrence(Incident incident, NormalizedEvent event, String fingerprint, Duration window) {
        dao.upsertOccurrence(converter.toOccurrencePo(incident, event, fingerprint, window));
    }

    @Override
    public List<IncidentOccurrenceSummary> findByIncidentId(String incidentId, int limit) {
        return dao.findByIncidentId(incidentId, cap(limit)).stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit, 1000));
    }
}
