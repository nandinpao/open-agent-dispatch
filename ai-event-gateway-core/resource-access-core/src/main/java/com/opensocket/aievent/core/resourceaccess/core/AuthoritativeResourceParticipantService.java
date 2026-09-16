package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.DescriptorResolutionContext;
import com.opensocket.aievent.core.resourceaccess.contract.ParticipantProjectionSnapshot;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceParticipantResolverPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Selects one trusted participant authority per ResourceType and rejects ambiguous projections. */
public final class AuthoritativeResourceParticipantService {
    private final Map<ResourceType, ResourceParticipantResolverPort> resolvers;
    public AuthoritativeResourceParticipantService(List<ResourceParticipantResolverPort> ports) {
        EnumMap<ResourceType, ResourceParticipantResolverPort> values = new EnumMap<>(ResourceType.class);
        for (ResourceParticipantResolverPort port : ports == null ? List.<ResourceParticipantResolverPort>of() : ports) {
            for (ResourceType type : ResourceType.values()) {
                if (port.supportsParticipants(type) && values.put(type, port) != null)
                    throw new IllegalArgumentException("DUPLICATE_RESOURCE_PARTICIPANT_RESOLVER: " + type);
            }
        }
        resolvers = Map.copyOf(values);
    }
    public ParticipantProjectionSnapshot resolve(ResourceRef resourceRef, DescriptorResolutionContext context,
                                                  com.opensocket.aievent.core.resourceaccess.contract.DescriptorAuthority fallbackAuthority) {
        ResourceParticipantResolverPort resolver = resolvers.get(resourceRef.resourceType());
        if (resolver == null) return ParticipantProjectionSnapshot.empty(resourceRef, fallbackAuthority, context.requestedAt());
        ParticipantProjectionSnapshot snapshot = resolver.resolveParticipants(resourceRef, context)
                .orElseGet(() -> ParticipantProjectionSnapshot.empty(resourceRef, fallbackAuthority, context.requestedAt()));
        if (!resourceRef.equals(snapshot.resourceRef())) throw new IllegalStateException("RESOURCE_PARTICIPANT_IDENTITY_MISMATCH");
        return snapshot;
    }
}
