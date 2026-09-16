package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceCatalogEntry;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import java.util.Collection;
import java.util.Optional;

public interface ResourceCatalog {
    Optional<ResourceCatalogEntry> find(ResourceType resourceType);
    Collection<ResourceCatalogEntry> entries();
    default ResourceCatalogEntry require(ResourceType resourceType) {
        return find(resourceType).orElseThrow(() -> new IllegalArgumentException("RESOURCE_TYPE_NOT_CATALOGED: " + resourceType));
    }
}
