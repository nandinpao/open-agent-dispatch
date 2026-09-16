package com.opensocket.aievent.core.iam.runtime.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IamGeneratedIdentityIdsTest {
    @Test
    void sameNamespaceAndIdempotencyKeyProduceRetryStableIdentityId() {
        String first = IamGeneratedIdentityIds.resolve(null, "TENANT:tenant-a:USER_CREATE", "request-123");
        String retry = IamGeneratedIdentityIds.resolve("", "TENANT:tenant-a:USER_CREATE", "request-123");
        String differentRequest = IamGeneratedIdentityIds.resolve(null, "TENANT:tenant-a:USER_CREATE", "request-456");
        String differentAuthority = IamGeneratedIdentityIds.resolve(null, "TENANT:tenant-b:USER_CREATE", "request-123");

        assertEquals(first, retry);
        assertNotEquals(first, differentRequest);
        assertNotEquals(first, differentAuthority);
    }

    @Test
    void suppliedControlledImportIdWins() {
        assertEquals("legacy-user-1", IamGeneratedIdentityIds.resolve(
                " legacy-user-1 ", "TENANT:tenant-a:USER_CREATE", "request-123"));
    }

    @Test
    void generatedIdentityRequiresAuthorityNamespaceAndIdempotencyKey() {
        assertThrows(IllegalArgumentException.class,
                () -> IamGeneratedIdentityIds.resolve(null, "", "request-123"));
        assertThrows(IllegalArgumentException.class,
                () -> IamGeneratedIdentityIds.resolve(null, "TENANT:tenant-a:USER_CREATE", ""));
    }
}
