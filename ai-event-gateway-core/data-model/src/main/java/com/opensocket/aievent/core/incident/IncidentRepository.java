package com.opensocket.aievent.core.incident;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository {
    Incident save(Incident incident);
    /**
     * Creates an ACTIVE incident only when no active incident exists for the same fingerprint.
     * Implementations must make this operation concurrency-safe.
     */
    default Incident saveNewOrGetActive(Incident incident) {
        return save(incident);
    }
    Optional<Incident> findById(String incidentId);
    Optional<Incident> findActiveByFingerprint(String fingerprint);
    default Optional<Incident> findLatestByFingerprint(String fingerprint) {
        return findActiveByFingerprint(fingerprint);
    }
    default List<Incident> findActiveLastSeenBefore(OffsetDateTime cutoff, int limit) {
        return List.of();
    }
    List<Incident> findAll();
    List<Incident> search(IncidentQuery query);
    String mode();
}
