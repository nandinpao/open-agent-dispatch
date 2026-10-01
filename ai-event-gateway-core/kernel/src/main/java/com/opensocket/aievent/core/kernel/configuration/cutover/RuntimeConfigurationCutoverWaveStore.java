package com.opensocket.aievent.core.kernel.configuration.cutover;

import java.util.List;
import java.util.Optional;

/** Persistence port for immutable/release-governed Runtime Configuration cutover wave definitions. */
public interface RuntimeConfigurationCutoverWaveStore {
    List<RuntimeConfigurationCutoverWave> list();
    Optional<RuntimeConfigurationCutoverWave> find(String waveId);
    Optional<RuntimeConfigurationCutoverWave> findBySetKey(String setKey);
}
