package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RuntimeLateResultQuarantinePort {
    RuntimeLateResultQuarantine append(RuntimeLateResultQuarantine record);
    Optional<RuntimeLateResultQuarantine> findBySubmission(String tenantId,String submissionId);
    List<RuntimeLateResultQuarantine> findOpen(String tenantId,int limit);
    RuntimeLateResultQuarantine resolve(String tenantId,String quarantineId,long expectedVersion,
            RuntimeLateResultQuarantineStatus target,String actorId,String reason,Instant at);
}
