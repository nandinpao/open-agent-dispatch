package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.resourceaccess.contract.PolicyVersion;
import com.opensocket.aievent.core.resourceaccess.contract.SecurityEpoch;
import java.util.Objects;

public record UiCapabilityAuthorityNamespace(PolicyVersion policyVersion, SecurityEpoch securityEpoch) {
    public UiCapabilityAuthorityNamespace {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(securityEpoch, "securityEpoch");
    }
}
