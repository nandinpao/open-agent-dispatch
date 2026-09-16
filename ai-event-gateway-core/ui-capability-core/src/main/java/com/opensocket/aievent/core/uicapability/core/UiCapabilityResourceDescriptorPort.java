package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import java.time.Instant;
import java.util.Optional;

/** Minimal read-only bridge to the authoritative server-side resource descriptor service. */
public interface UiCapabilityResourceDescriptorPort {
    Optional<ResourceDescriptor> resolve(ResourceRef resourceRef, String correlationId, Instant requestedAt);
}
