package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.DescriptorResolutionContext;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceDescriptorResolverPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves descriptors only through trusted server-side bridges and rejects identity or tenant drift. */
public final class AuthoritativeResourceDescriptorService {
    private final ResourceCatalog catalog;
    private final Map<ResourceType, ResourceDescriptorResolverPort> resolvers;
    public AuthoritativeResourceDescriptorService(ResourceCatalog catalog, List<ResourceDescriptorResolverPort> resolverPorts) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        EnumMap<ResourceType, ResourceDescriptorResolverPort> map = new EnumMap<>(ResourceType.class);
        for (ResourceDescriptorResolverPort resolver : resolverPorts == null ? List.<ResourceDescriptorResolverPort>of() : resolverPorts) {
            for (ResourceType type : ResourceType.values()) {
                if (resolver.supports(type) && map.put(type, resolver) != null) throw new IllegalArgumentException("DUPLICATE_RESOURCE_DESCRIPTOR_RESOLVER: " + type);
            }
        }
        this.resolvers = Map.copyOf(map);
    }
    public ResourceDescriptor resolve(ResourceRef resourceRef, DescriptorResolutionContext context) {
        Objects.requireNonNull(resourceRef, "resourceRef"); Objects.requireNonNull(context, "context");
        catalog.require(resourceRef.resourceType());
        ResourceDescriptorResolverPort resolver = resolvers.get(resourceRef.resourceType());
        if (resolver == null) throw new IllegalStateException("RESOURCE_DESCRIPTOR_RESOLVER_NOT_REGISTERED: " + resourceRef.resourceType());
        ResourceDescriptor descriptor = resolver.resolve(resourceRef, context)
                .orElseThrow(() -> new IllegalArgumentException("RESOURCE_DESCRIPTOR_NOT_FOUND"));
        if (!descriptor.resourceRef().equals(resourceRef)) throw new IllegalStateException("RESOURCE_DESCRIPTOR_IDENTITY_MISMATCH");
        if (!descriptor.resourceRef().tenantId().equals(resourceRef.tenantId())) throw new IllegalStateException("RESOURCE_DESCRIPTOR_TENANT_MISMATCH");
        return descriptor;
    }
}
