package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Optional;

/** Trusted server-side participant source. It must read the existing Domain authority, never projection tables. */
public interface ResourceParticipantResolverPort {
    boolean supportsParticipants(ResourceType resourceType);
    Optional<ParticipantProjectionSnapshot> resolveParticipants(ResourceRef resourceRef, DescriptorResolutionContext context);
}
