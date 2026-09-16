package com.opensocket.aievent.core.iam.api.config;

import com.opensocket.aievent.core.iam.api.application.port.IamAdministrationProjectionPort;
import com.opensocket.aievent.core.iam.api.application.port.IamAuthenticationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamBootstrapApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamCredentialAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSecurityPolicyApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserProvisioningApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserInvitationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserLifecycleApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamPlatformUserAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionCookiePort;
import com.opensocket.aievent.core.iam.api.application.port.IamOneTimeSecretDeliveryPort;
import com.opensocket.aievent.core.iam.api.application.service.IamOrganizationAdministrationService;
import com.opensocket.aievent.core.iam.api.controller.IamUserInvitationController;
import com.opensocket.aievent.core.iam.api.controller.IamUserLifecycleController;
import com.opensocket.aievent.core.iam.api.application.service.IamRbacAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamTokenAdministrationService;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyPort;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamSecurityAdapter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

/** Fails startup when the IAM API feature flag is enabled with only a partial composition. */
public final class IamApiActivationValidator implements SmartInitializingSingleton {
    private final ApplicationContext context;

    public IamApiActivationValidator(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<Class<?>> required = List.of(
                IamIdempotencyPort.class,
                IamAdministrationProjectionPort.class,
                IamBootstrapApiPort.class,
                IamAuthenticationApiPort.class,
                IamSecurityPolicyApiPort.class,
                IamUserProvisioningApiPort.class,
                IamUserAdministrationApiPort.class,
                IamUserLifecycleApiPort.class,
                IamUserInvitationApiPort.class,
                IamPlatformUserAdministrationApiPort.class,
                IamSessionAdministrationApiPort.class,
                IamCredentialAdministrationApiPort.class,
                IamSessionCookiePort.class,
                IamOneTimeSecretDeliveryPort.class,
                IamSecurityAdapter.class,
                IamPermissionGuard.class,
                IamOrganizationAdministrationService.class,
                IamRbacAdministrationService.class,
                IamTokenAdministrationService.class,
                IamUserLifecycleController.class,
                IamUserInvitationController.class);
        List<String> missing = new ArrayList<>();
        for (Class<?> type : required) {
            if (context.getBeanNamesForType(type, false, false).length == 0) {
                missing.add(type.getSimpleName());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "IAM_API_COMPOSITION_INCOMPLETE: missing required beans " + String.join(", ", missing));
        }
    }
}
