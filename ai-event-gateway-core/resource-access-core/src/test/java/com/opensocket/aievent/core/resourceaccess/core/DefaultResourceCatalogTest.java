package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import org.junit.jupiter.api.Test;

class DefaultResourceCatalogTest {
    @Test void catalogsEveryStableResourceTypeExactlyOnce() {
        ResourceCatalog catalog = new DefaultResourceCatalog();
        assertEquals(ResourceType.values().length, catalog.entries().size());
        for (ResourceType type : ResourceType.values()) assertEquals(type, catalog.require(type).resourceType());
    }
    @Test void credentialResourcesAreSecretAndVisibilityControlled() {
        ResourceCatalog catalog = new DefaultResourceCatalog();
        assertEquals(SensitivityLevel.SECRET, catalog.require(ResourceType.ISSUE_CREDENTIAL_METADATA).defaultSensitivity());
        assertTrue(catalog.require(ResourceType.ISSUE_CREDENTIAL_METADATA).fieldVisibilitySupported());
    }
}
