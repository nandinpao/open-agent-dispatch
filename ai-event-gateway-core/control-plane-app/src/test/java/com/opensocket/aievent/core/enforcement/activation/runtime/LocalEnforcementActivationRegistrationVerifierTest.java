package com.opensocket.aievent.core.enforcement.activation.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opensocket.aievent.core.api.EnforcementActivationController;
import com.opensocket.aievent.core.api.TaskReadCertificationController;
import com.opensocket.aievent.core.api.Wave0ReadPilotController;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class LocalEnforcementActivationRegistrationVerifierTest {
    @Test
    void rejectsDisabledLocalControlPlane() {
        ApplicationContext context = mock(ApplicationContext.class);
        LocalEnforcementActivationRegistrationVerifier verifier =
                new LocalEnforcementActivationRegistrationVerifier(context, false);

        IllegalStateException error = assertThrows(IllegalStateException.class, verifier::verify);
        assertEquals("LOCAL_ENFORCEMENT_ACTIVATION_CONTROL_PLANE_REQUIRED", error.getMessage());
    }

    @Test
    void rejectsMissingControllerRegistration() {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeansOfType(EnforcementActivationController.class)).thenReturn(Map.of());
        when(context.getBeansOfType(Wave0ReadPilotController.class)).thenReturn(Map.of());
        when(context.getBeansOfType(TaskReadCertificationController.class)).thenReturn(Map.of());
        LocalEnforcementActivationRegistrationVerifier verifier =
                new LocalEnforcementActivationRegistrationVerifier(context, true);

        IllegalStateException error = assertThrows(IllegalStateException.class, verifier::verify);
        assertEquals(
                "LOCAL_ENFORCEMENT_ACTIVATION_CONTROLLERS_NOT_REGISTERED "
                        + "missing=EnforcementActivationController,Wave0ReadPilotController,"
                        + "TaskReadCertificationController",
                error.getMessage());
    }

    @Test
    void acceptsRegisteredController() {
        ApplicationContext context = mock(ApplicationContext.class);
        EnforcementActivationController controller = mock(EnforcementActivationController.class);
        Wave0ReadPilotController wave0 = mock(Wave0ReadPilotController.class);
        TaskReadCertificationController taskRead = mock(TaskReadCertificationController.class);
        when(context.getBeansOfType(EnforcementActivationController.class))
                .thenReturn(Map.of("enforcementActivationController", controller));
        when(context.getBeansOfType(Wave0ReadPilotController.class))
                .thenReturn(Map.of("wave0ReadPilotController", wave0));
        when(context.getBeansOfType(TaskReadCertificationController.class))
                .thenReturn(Map.of("taskReadCertificationController", taskRead));
        LocalEnforcementActivationRegistrationVerifier verifier =
                new LocalEnforcementActivationRegistrationVerifier(context, true);

        assertDoesNotThrow(verifier::verify);
    }
}
