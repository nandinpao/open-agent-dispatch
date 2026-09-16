package com.opensocket.aievent.core.enforcement.activation.runtime;

import com.opensocket.aievent.core.api.EnforcementActivationController;
import com.opensocket.aievent.core.api.TaskReadCertificationController;
import com.opensocket.aievent.core.api.Wave0ReadPilotController;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Fails local startup when the administration UI advertises Enforcement Activation but its controller is absent. */
@Component
@Profile("local")
public final class LocalEnforcementActivationRegistrationVerifier {
    private final ApplicationContext applicationContext;
    private final boolean controlPlaneEnabled;

    public LocalEnforcementActivationRegistrationVerifier(
            ApplicationContext applicationContext,
            @Value("${aeg.enforcement-activation.control-plane-enabled:false}") boolean controlPlaneEnabled) {
        this.applicationContext = applicationContext;
        this.controlPlaneEnabled = controlPlaneEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (!controlPlaneEnabled) {
            throw new IllegalStateException(
                    "LOCAL_ENFORCEMENT_ACTIVATION_CONTROL_PLANE_REQUIRED");
        }
        List<String> missing = new ArrayList<>();
        requireController(EnforcementActivationController.class, missing);
        requireController(Wave0ReadPilotController.class, missing);
        requireController(TaskReadCertificationController.class, missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "LOCAL_ENFORCEMENT_ACTIVATION_CONTROLLERS_NOT_REGISTERED missing="
                            + String.join(",", missing));
        }
    }

    private void requireController(Class<?> controllerType, List<String> missing) {
        if (applicationContext.getBeansOfType(controllerType).isEmpty()) {
            missing.add(controllerType.getSimpleName());
        }
    }
}
