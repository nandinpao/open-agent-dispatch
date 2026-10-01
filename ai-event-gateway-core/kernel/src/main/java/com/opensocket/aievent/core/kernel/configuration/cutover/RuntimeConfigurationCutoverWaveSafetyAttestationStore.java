package com.opensocket.aievent.core.kernel.configuration.cutover;

import java.util.Optional;

/** Persistence port for durable high-risk cutover safety attestations. */
public interface RuntimeConfigurationCutoverWaveSafetyAttestationStore {
    RuntimeConfigurationCutoverWaveSafetyAttestation save(RuntimeConfigurationCutoverWaveSafetyAttestation attestation);
    Optional<RuntimeConfigurationCutoverWaveSafetyAttestation> latest(String waveId);
}
