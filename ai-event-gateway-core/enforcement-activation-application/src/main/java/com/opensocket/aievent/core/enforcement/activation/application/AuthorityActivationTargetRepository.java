package com.opensocket.aievent.core.enforcement.activation.application;

import java.util.Optional;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityActivationTarget;

public interface AuthorityActivationTargetRepository {
    Optional<AuthorityActivationTarget> current();
}
