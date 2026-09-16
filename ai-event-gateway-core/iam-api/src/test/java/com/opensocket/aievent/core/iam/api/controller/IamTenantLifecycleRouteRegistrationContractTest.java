package com.opensocket.aievent.core.iam.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class IamTenantLifecycleRouteRegistrationContractTest {

    @Test
    void onboardingUsesExactTenantAddressedRouteWithoutTrailingSlashDependency() throws Exception {
        RequestMapping typeMapping = IamUserLifecycleController.class.getAnnotation(RequestMapping.class);
        assertTrue(Arrays.asList(typeMapping.value())
                .contains("/api/admin/access/tenants/{tenantId}/user-onboarding"));

        Method onboard = Arrays.stream(IamUserLifecycleController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("onboard"))
                .findFirst()
                .orElseThrow();
        PostMapping postMapping = onboard.getAnnotation(PostMapping.class);
        assertEquals(0, postMapping.value().length,
                "The canonical route must match without requiring a trailing slash");
        assertEquals(0, postMapping.path().length,
                "The canonical route must match without requiring a trailing slash");
    }

    @Test
    void lifecycleControllersDoNotDependOnConditionalBeanProcessingOrder() {
        assertNull(IamUserLifecycleController.class.getAnnotation(ConditionalOnBean.class));
        assertNull(IamUserInvitationController.class.getAnnotation(ConditionalOnBean.class));
    }
}
