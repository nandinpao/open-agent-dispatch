package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Optional;

/** Implemented only by trusted runtime bridges; never by Controllers or request DTO mappers. */
public interface ResourceDescriptorResolverPort {
    boolean supports(ResourceType resourceType);
    Optional<ResourceDescriptor> resolve(ResourceRef resourceRef, DescriptorResolutionContext context);
}
