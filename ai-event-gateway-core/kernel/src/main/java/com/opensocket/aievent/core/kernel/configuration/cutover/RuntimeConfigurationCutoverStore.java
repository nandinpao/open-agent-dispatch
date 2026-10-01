package com.opensocket.aievent.core.kernel.configuration.cutover;

import java.util.Optional;

/** Persistence port for generic two-phase Runtime Configuration cutover plans. */
public interface RuntimeConfigurationCutoverStore {
    Optional<RuntimeConfigurationCutoverPlan> findPrepared(String configSetId);
    Optional<RuntimeConfigurationCutoverPlan> findLatest(String configSetId);
    RuntimeConfigurationCutoverPlan createPrepared(RuntimeConfigurationCutoverPlan plan);
    RuntimeConfigurationCutoverPlan markFinalized(String cutoverId,long expectedVersion,String actor,String reason);
    RuntimeConfigurationCutoverPlan markCancelled(String cutoverId,long expectedVersion,String actor,String reason);
}
