package com.opensocket.aievent.core.resourceaccess.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IamResourcePermissionAuthorityAdapterTest {

    @Test
    void blankAndStarAreWorkloadWildcards() {
        assertTrue(IamResourcePermissionAuthorityAdapter.matchesScopeCode(null, "ERP"));
        assertTrue(IamResourcePermissionAuthorityAdapter.matchesScopeCode("", "ERP"));
        assertTrue(IamResourcePermissionAuthorityAdapter.matchesScopeCode("*", "ERP"));
    }

    @Test
    void explicitCodesRemainCaseInsensitiveAndFailClosedOnMismatch() {
        assertTrue(IamResourcePermissionAuthorityAdapter.matchesScopeCode("erp", "ERP"));
        assertFalse(IamResourcePermissionAuthorityAdapter.matchesScopeCode("MES", "ERP"));
        assertFalse(IamResourcePermissionAuthorityAdapter.matchesScopeCode("ERP", null));
    }
}
