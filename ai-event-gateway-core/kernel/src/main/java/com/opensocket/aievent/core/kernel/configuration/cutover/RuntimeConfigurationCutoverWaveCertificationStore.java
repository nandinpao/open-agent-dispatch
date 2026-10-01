package com.opensocket.aievent.core.kernel.configuration.cutover;

import java.util.Optional;

/** Persistence port for durable cutover-wave runtime convergence certification evidence. */
public interface RuntimeConfigurationCutoverWaveCertificationStore {
    RuntimeConfigurationCutoverWaveCertification save(RuntimeConfigurationCutoverWaveCertification certification);
    Optional<RuntimeConfigurationCutoverWaveCertification> latest(String waveId);
}
