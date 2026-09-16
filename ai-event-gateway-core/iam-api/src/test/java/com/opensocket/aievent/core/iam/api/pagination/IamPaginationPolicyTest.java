package com.opensocket.aievent.core.iam.api.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opensocket.aievent.core.iam.api.config.IamApiProperties;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IamPaginationPolicyTest {
    private final IamPaginationPolicy policy = new IamPaginationPolicy(new IamApiProperties(
            false,
            20,
            100,
            Duration.ofMinutes(15),
            "",
            true,
            true));

    @Test
    void appliesDefaultsAndBounds() {
        assertEquals(20, policy.limit(0));
        assertEquals(100, policy.size(100));
        assertEquals(0, policy.page(0));
    }

    @Test
    void capsLegacyReadOnlyDirectoryRequests() {
        assertEquals(100, policy.sizeCapped(250));
        assertEquals(20, policy.sizeCapped(0));
    }

    @Test
    void rejectsUnboundedQueries() {
        IamApiException size = assertThrows(IamApiException.class, () -> policy.size(101));
        assertEquals("IAM_PAGE_SIZE_EXCEEDED", size.errorCode());
        IamApiException page = assertThrows(IamApiException.class, () -> policy.page(-1));
        assertEquals("IAM_PAGE_INVALID", page.errorCode());
    }
}
