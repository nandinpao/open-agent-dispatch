package com.opensocket.aievent.core.iam.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opensocket.aievent.core.iam.api.application.port.IamAdministrationProjectionPort;
import com.opensocket.aievent.core.iam.api.application.port.IamAuthenticationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamBootstrapApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamCredentialAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamOneTimeSecretDeliveryPort;
import com.opensocket.aievent.core.iam.api.application.port.IamPlatformUserAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSecurityPolicyApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionCookiePort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserProvisioningApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserInvitationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserLifecycleApiPort;
import com.opensocket.aievent.core.iam.api.application.service.IamOrganizationAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamRbacAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamTokenAdministrationService;
import com.opensocket.aievent.core.iam.api.config.IamApiApplicationConfiguration;
import com.opensocket.aievent.core.iam.api.config.IamApiConfiguration;
import com.opensocket.aievent.core.iam.api.controller.IamUserInvitationController;
import com.opensocket.aievent.core.iam.api.controller.IamUserLifecycleController;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyPort;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamSecurityAdapter;
import com.opensocket.aievent.core.iam.api.security.IamSecurityAdapterConfiguration;
import com.opensocket.aievent.core.iam.organization.application.port.in.DepartmentCommandPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.GroupCommandPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.OrganizationQueryPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.AuthorizationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.EntryPointAuthorityAdministrationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.PermissionCatalogAdministrationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.RbacAdministrationPort;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.iam.token.application.port.in.ServiceAccountCommandPort;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Reproduces the production ordering where IAM persistence ports arrive from
 * auto-configuration after the component-scanned IAM API configurations.
 */
class IamApiLateAutoConfigurationCompositionTest {
    private static final String CURSOR_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    IamApiConfiguration.class,
                    IamApiApplicationConfiguration.class,
                    IamUserLifecycleController.class,
                    IamUserInvitationController.class,
                    IamSecurityAdapterConfiguration.class,
                    ApiInfrastructureConfiguration.class)
            .withConfiguration(AutoConfigurations.of(LateIamPortAutoConfiguration.class))
            .withPropertyValues(
                    "aeg.iam.api.enabled=true",
                    "aeg.iam.api.cursor-signing-secret-base64=" + CURSOR_SECRET);

    @Test
    void apiBeansRemainComposableWhenRequiredPortsArriveFromLaterAutoConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IamSecurityAdapter.class);
            assertThat(context).hasSingleBean(IamPermissionGuard.class);
            assertThat(context).hasSingleBean(IamOrganizationAdministrationService.class);
            assertThat(context).hasSingleBean(IamRbacAdministrationService.class);
            assertThat(context).hasSingleBean(IamTokenAdministrationService.class);
            assertThat(context).hasSingleBean(IamUserLifecycleController.class);
            assertThat(context).hasSingleBean(IamUserInvitationController.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ApiInfrastructureConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @AutoConfiguration
    static class LateIamPortAutoConfiguration {
        @Bean IamIdempotencyPort idempotencyPort() { return mock(IamIdempotencyPort.class); }
        @Bean AuthorizationPort authorizationPort() { return mock(AuthorizationPort.class); }
        @Bean TenantCommandPort tenantCommandPort() { return mock(TenantCommandPort.class); }
        @Bean DepartmentCommandPort departmentCommandPort() { return mock(DepartmentCommandPort.class); }
        @Bean GroupCommandPort groupCommandPort() { return mock(GroupCommandPort.class); }
        @Bean OrganizationQueryPort organizationQueryPort() { return mock(OrganizationQueryPort.class); }
        @Bean RbacAdministrationPort rbacAdministrationPort() { return mock(RbacAdministrationPort.class); }
        @Bean PermissionCatalogAdministrationPort permissionCatalogAdministrationPort() { return mock(PermissionCatalogAdministrationPort.class); }
        @Bean EntryPointAuthorityAdministrationPort entryPointAuthorityAdministrationPort() { return mock(EntryPointAuthorityAdministrationPort.class); }
        @Bean AccessTokenCommandPort accessTokenCommandPort() { return mock(AccessTokenCommandPort.class); }
        @Bean ServiceAccountCommandPort serviceAccountCommandPort() { return mock(ServiceAccountCommandPort.class); }

        @Bean IamAdministrationProjectionPort administrationProjectionPort() { return mock(IamAdministrationProjectionPort.class); }
        @Bean IamBootstrapApiPort bootstrapApiPort() { return mock(IamBootstrapApiPort.class); }
        @Bean IamAuthenticationApiPort authenticationApiPort() { return mock(IamAuthenticationApiPort.class); }
        @Bean IamSecurityPolicyApiPort securityPolicyApiPort() { return mock(IamSecurityPolicyApiPort.class); }
        @Bean IamUserProvisioningApiPort userProvisioningApiPort() { return mock(IamUserProvisioningApiPort.class); }
        @Bean IamUserAdministrationApiPort userAdministrationApiPort() { return mock(IamUserAdministrationApiPort.class); }
        @Bean IamUserLifecycleApiPort userLifecycleApiPort() { return mock(IamUserLifecycleApiPort.class); }
        @Bean IamUserInvitationApiPort userInvitationApiPort() { return mock(IamUserInvitationApiPort.class); }
        @Bean IamPlatformUserAdministrationApiPort platformUserAdministrationApiPort() { return mock(IamPlatformUserAdministrationApiPort.class); }
        @Bean IamSessionAdministrationApiPort sessionAdministrationApiPort() { return mock(IamSessionAdministrationApiPort.class); }
        @Bean IamCredentialAdministrationApiPort credentialAdministrationApiPort() { return mock(IamCredentialAdministrationApiPort.class); }
        @Bean IamSessionCookiePort sessionCookiePort() { return mock(IamSessionCookiePort.class); }
        @Bean IamOneTimeSecretDeliveryPort oneTimeSecretDeliveryPort() { return mock(IamOneTimeSecretDeliveryPort.class); }
    }
}
