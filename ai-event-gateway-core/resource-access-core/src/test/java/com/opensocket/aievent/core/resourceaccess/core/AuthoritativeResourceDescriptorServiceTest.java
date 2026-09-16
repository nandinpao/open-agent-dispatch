package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthoritativeResourceDescriptorServiceTest {
    @Test void resolvesOnlyAnIdentityMatchingServerDescriptor() {
        ResourceRef ref = new ResourceRef("tenant-a", ResourceType.TASK, "task-1");
        ResourceDescriptor descriptor = descriptor(ref);
        ResourceDescriptorResolverPort resolver = new ResourceDescriptorResolverPort() {
            public boolean supports(ResourceType type) { return type == ResourceType.TASK; }
            public Optional<ResourceDescriptor> resolve(ResourceRef requested, DescriptorResolutionContext context) { return Optional.of(descriptor); }
        };
        AuthoritativeResourceDescriptorService service = new AuthoritativeResourceDescriptorService(new DefaultResourceCatalog(), List.of(resolver));
        assertEquals(descriptor, service.resolve(ref, new DescriptorResolutionContext("corr-1", "test", Instant.now())));
    }
    @Test void rejectsAResolverThatReturnsAnotherResource() {
        ResourceRef ref = new ResourceRef("tenant-a", ResourceType.TASK, "task-1");
        ResourceDescriptorResolverPort resolver = new ResourceDescriptorResolverPort() {
            public boolean supports(ResourceType type) { return type == ResourceType.TASK; }
            public Optional<ResourceDescriptor> resolve(ResourceRef requested, DescriptorResolutionContext context) {
                return Optional.of(descriptor(new ResourceRef("tenant-a", ResourceType.TASK, "task-2")));
            }
        };
        AuthoritativeResourceDescriptorService service = new AuthoritativeResourceDescriptorService(new DefaultResourceCatalog(), List.of(resolver));
        assertThrows(IllegalStateException.class, () -> service.resolve(ref, new DescriptorResolutionContext("corr-1", "test", Instant.now())));
    }
    private static ResourceDescriptor descriptor(ResourceRef ref) {
        return new ResourceDescriptor(ref, ref.resourceId(), OwnershipDescriptor.unowned(1), null, ref,
                new VisibilityDescriptor(SensitivityLevel.INTERNAL, VisibilityLevel.STANDARD, "default", PolicyVersion.ZERO),
                ResourceSecurityState.NORMAL, 1, 1, DescriptorAuthority.TASK_DOMAIN, "hash", Instant.now());
    }
}
