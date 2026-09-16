package com.opensocket.aievent.core.uicapability.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CanonicalUiActionCatalogResolverTest {
    @Test void exposesAllNormalizedActionsServerSideOnly() {
        CanonicalUiActionCatalogResolver catalog = new CanonicalUiActionCatalogResolver();
        assertEquals(58, catalog.size());
        assertEquals("a2a.request.cancel", catalog.resolve("a2a.request.cancel").orElseThrow()
                .resourceAction().permissionCode());
        assertEquals("task.update", catalog.resolve("task.cancel.execute").orElseThrow()
                .resourceAction().permissionCode());
        assertTrue(catalog.resolve("task.cancel.execute").orElseThrow().resourceAction().sideEffecting());
        assertTrue(catalog.resolve("unknown.action.read").isEmpty());
    }
}
