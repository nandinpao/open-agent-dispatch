package com.opensocket.aievent.core.enforcement.activation.application;

import java.util.Optional;
import java.util.UUID;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceBinding;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;

public interface ReadinessEvidenceRepository {
    Optional<ReadinessEvidenceBinding> resolve(ReadinessEvidenceType type, UUID evidenceId);
}
