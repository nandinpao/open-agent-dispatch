package com.opensocket.aievent.core.enforcement.activation.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.opensocket.aievent.core.api.EnforcementActivationController;
import com.opensocket.aievent.core.api.TaskReadCertificationController;
import com.opensocket.aievent.core.api.Wave0ReadPilotController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class EnforcementActivationControllerRegistrationContractTest {
    @Test
    void componentScannedControllersDoNotDependOnLateBeanConditionOrdering() {
        assertControllerCondition(EnforcementActivationController.class);
        assertControllerCondition(Wave0ReadPilotController.class);
        assertControllerCondition(TaskReadCertificationController.class);
    }

    private static void assertControllerCondition(Class<?> controllerType) {
        assertNull(controllerType.getAnnotation(ConditionalOnBean.class));
        assertNotNull(controllerType.getAnnotation(ConditionalOnProperty.class));
    }
}
