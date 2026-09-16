package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Optional;

/** Atomic tenant cutover boundary. Activation must retire the previous revision and invalidate old snapshots. */
public interface DepartmentRevisionCutoverPort {
    Optional<DepartmentRevisionCutover> current(String tenantId);
    DepartmentRevisionCutover prepare(String tenantId,long departmentRevision,long preparedSnapshotCount,String actorId,String correlationId,Instant at);
    DepartmentRevisionCutover activate(String tenantId,String cutoverId,long expectedVersion,String actorId,String correlationId,Instant at);
    DepartmentRevisionCutover fail(String tenantId,String cutoverId,long expectedVersion,String reasonCode,String actorId,String correlationId,Instant at);
}
