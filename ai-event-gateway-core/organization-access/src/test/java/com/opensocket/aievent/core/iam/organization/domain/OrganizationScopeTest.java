package com.opensocket.aievent.core.iam.organization.domain;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrganizationScopeTest {
    @Test void primaryDepartmentMustBePartOfResolvedDepartments() {
        assertThrows(IllegalArgumentException.class, () -> new OrganizationScope(
                new TenantId("tenant-a"), new PrincipalRef(PrincipalRef.PrincipalType.USER, "u-1"),
                MembershipStatus.ACTIVE, Optional.of(new DepartmentId("d-1")), Set.of(), Set.of(), Instant.now(), 1));
    }
}
