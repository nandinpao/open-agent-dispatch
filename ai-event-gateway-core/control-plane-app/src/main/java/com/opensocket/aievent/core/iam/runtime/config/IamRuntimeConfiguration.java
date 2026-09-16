package com.opensocket.aievent.core.iam.runtime.config;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.iam.api.application.port.*;
import com.opensocket.aievent.core.iam.api.application.service.IamEffectiveAccessQueryService;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyPort;
import com.opensocket.aievent.core.iam.api.pagination.SignedCursorCodec;
import com.opensocket.aievent.core.iam.api.security.IamSecurityAdapter;
import com.opensocket.aievent.core.iam.authentication.application.port.in.*;
import com.opensocket.aievent.core.iam.authentication.application.port.out.*;
import com.opensocket.aievent.core.iam.identity.application.port.in.*;
import com.opensocket.aievent.core.iam.organization.application.port.in.*;
import com.opensocket.aievent.core.iam.organization.application.port.out.DepartmentRepository;
import com.opensocket.aievent.core.iam.persistence.dao.*;
import com.opensocket.aievent.core.iam.persistence.outbox.IamTransactionalOutboxWriter;
import com.opensocket.aievent.core.iam.rbac.application.port.in.RbacAdministrationPort;
import com.opensocket.aievent.core.iam.runtime.idempotency.TransactionalIamIdempotencyAdapter;
import com.opensocket.aievent.core.iam.runtime.credential.*;
import com.opensocket.aievent.core.iam.runtime.orchestration.*;
import com.opensocket.aievent.core.iam.runtime.projection.MybatisIamAdministrationProjectionAdapter;
import com.opensocket.aievent.core.iam.api.application.port.IamMachineOwnershipAdministrationPort;
import com.opensocket.aievent.core.iam.runtime.projection.MybatisIamMachineOwnershipAdministrationAdapter;
import com.opensocket.aievent.core.iam.runtime.projection.MybatisIamUiSessionAdapter;
import com.opensocket.aievent.core.iam.runtime.security.*;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.identity.AdminIdentityRepository;
import com.opensocket.aievent.core.security.R3CompatibilityDecisionEvaluator;
import com.opensocket.aievent.core.security.R3HumanApiPermissionRegistry;
import com.opensocket.aievent.core.security.R3HttpAuthorizationFilter;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 1A-7 composition root for executable IAM runtime adapters.
 * The configuration is deliberately all-or-nothing: no partial IAM API activation is supported.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({IamRuntimeProperties.class, LegacyCredentialAdapterProperties.class, IamFederationProperties.class})
@ConditionalOnProperty(prefix = "aeg.iam.runtime", name = "enabled", havingValue = "true")
public class IamRuntimeConfiguration implements EnvironmentAware {
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Bean
    SmartInitializingSingleton iamRuntimeActivationValidator(IamRuntimeProperties properties) {
        return () -> {
            properties.validateEnabled();
            if (properties.isActivationEmailEnabled()) {
                requireConfigured("spring.mail.host", "SPRING_MAIL_HOST", "activation email");
            }
            requireEnabled("aeg.iam.api.enabled");
            requireEnabled("aeg.iam.authentication.enabled");
            requireEnabled("aeg.iam.rbac.enabled");
            requireEnabled("aeg.iam.token.enabled");
        };
    }

    @Bean
    TransactionTemplate iamRuntimeTransactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    IamSessionCookieCodec iamSessionCookieCodec(IamRuntimeProperties properties, Clock clock) {
        return new IamSessionCookieCodec(properties.requireSigningKey(), clock);
    }

    @Bean
    ServletIamSessionCookieAdapter iamSessionCookiePort(
            IamRuntimeProperties properties,
            IamSessionCookieCodec codec,
            Clock clock) {
        return new ServletIamSessionCookieAdapter(properties, codec, clock);
    }

    @Bean
    SecureFileOneTimeSecretDeliveryAdapter iamOneTimeSecretDeliveryPort(
            IamRuntimeProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        return new SecureFileOneTimeSecretDeliveryAdapter(
                properties.getOneTimeSecretDeliveryDirectory(), objectMapper, clock);
    }

    @Bean
    IamActivationDeliveryPort iamActivationDeliveryPort(
            IamApiRuntimeDao dao,
            IamOneTimeSecretDeliveryPort developmentFileDelivery,
            ObjectProvider<JavaMailSender> mailSender,
            IamRuntimeProperties properties,
            Clock clock,
            TransactionTemplate iamRuntimeTransactionTemplate) {
        return new TrackedActivationDeliveryAdapter(
                dao, developmentFileDelivery, mailSender.getIfAvailable(), properties, clock, iamRuntimeTransactionTemplate);
    }

    @Bean
    IamIdempotencyPort iamIdempotencyPort(
            IamApiRuntimeDao dao,
            TransactionTemplate iamRuntimeTransactionTemplate,
            Clock clock) {
        return new TransactionalIamIdempotencyAdapter(dao, iamRuntimeTransactionTemplate, clock);
    }

    @Bean
    IamAdministrationProjectionPort iamAdministrationProjectionPort(
            IamApiRuntimeDao dao,
            SignedCursorCodec cursors,
            TransactionTemplate iamRuntimeTransactionTemplate) {
        return new MybatisIamAdministrationProjectionAdapter(dao, cursors, iamRuntimeTransactionTemplate);
    }

    @Bean
    IamMachineOwnershipAdministrationPort iamMachineOwnershipAdministrationPort(
            IamApiRuntimeDao dao,
            TransactionTemplate iamRuntimeTransactionTemplate) {
        return new MybatisIamMachineOwnershipAdministrationAdapter(dao, iamRuntimeTransactionTemplate);
    }

    @Bean
    IamUiSessionApiPort iamUiSessionApiPort(
            IamApiRuntimeDao dao,
            TransactionTemplate iamRuntimeTransactionTemplate,
            PasswordCredentialRepository credentials,
            RootBootstrapStateRepository bootstrapStates,
            IamEffectiveAccessQueryService effectiveAccess) {
        return new MybatisIamUiSessionAdapter(
                dao, iamRuntimeTransactionTemplate, credentials, bootstrapStates, effectiveAccess);
    }

    @Bean
    IamLoginChallengeService iamLoginChallengeService(
            IamApiRuntimeDao dao,
            TransactionTemplate iamRuntimeTransactionTemplate,
            Clock clock,
            IamRuntimeProperties properties) {
        return new IamLoginChallengeService(dao, iamRuntimeTransactionTemplate, clock, properties);
    }

    @Bean
    FederationStateCodec federationStateCodec(IamFederationProperties properties) {
        return new FederationStateCodec(properties.requireStateSecret());
    }

    @Bean
    FederationClientSecretResolver federationClientSecretResolver(Environment environment) {
        return new FederationClientSecretResolver(environment);
    }

    @Bean
    OidcProtocolClient oidcProtocolClient(FederationClientSecretResolver secrets) {
        return new OidcProtocolClient(secrets);
    }

    @Bean
    IamFederationRuntimeOrchestrator iamFederationRuntimeOrchestrator(
            IamApiRuntimeDao dao,
            TransactionTemplate iamRuntimeTransactionTemplate,
            IamFederationProperties federationProperties,
            FederationStateCodec stateCodec,
            OidcProtocolClient oidc,
            IdentityQueryPort identityQueries,
            IdentityCommandPort identities,
            TenantCommandPort tenants,
            SessionCommandPort sessions,
            SessionPolicyRepository sessionPolicies,
            IamIdempotencyExecutor idempotency,
            Clock clock) {
        return new IamFederationRuntimeOrchestrator(
                dao, iamRuntimeTransactionTemplate, federationProperties, stateCodec, oidc,
                identityQueries, identities, tenants, sessions, sessionPolicies, idempotency, clock);
    }

    @Bean
    IamRuntimeSessionAuthenticator iamRuntimeSessionAuthenticator(
            BrowserSessionRepository sessions,
            SessionCommandPort commands,
            SessionPolicyRepository policies,
            SecurityEpochPort epochs,
            IamApiRuntimeDao dao,
            TransactionTemplate iamRuntimeTransactionTemplate,
            Clock clock,
            IamRuntimeProperties properties) {
        return new IamRuntimeSessionAuthenticator(
                sessions, commands, policies, epochs, dao, iamRuntimeTransactionTemplate, clock, properties);
    }

    @Bean
    IamBrowserSessionAuthenticationFilter iamBrowserSessionAuthenticationFilter(
            ServletIamSessionCookieAdapter cookies,
            IamSessionCookieCodec codec,
            IamRuntimeSessionAuthenticator authenticator) {
        return new IamBrowserSessionAuthenticationFilter(cookies, codec, authenticator);
    }

    @Bean
    FilterRegistrationBean<IamBrowserSessionAuthenticationFilter> disableIamBrowserSessionAuthenticationFilterServletRegistration(
            IamBrowserSessionAuthenticationFilter filter) {
        FilterRegistrationBean<IamBrowserSessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    A2AAgentMachineAuthenticationFilter a2aAgentMachineAuthenticationFilter(
            com.opensocket.aievent.core.agent.governance.AgentGovernanceService governance,
            ServletIamSessionCookieAdapter sessionCookies) {
        return new A2AAgentMachineAuthenticationFilter(governance, sessionCookies);
    }

    @Bean
    FilterRegistrationBean<A2AAgentMachineAuthenticationFilter> disableA2AAgentMachineAuthenticationFilterServletRegistration(
            A2AAgentMachineAuthenticationFilter filter) {
        FilterRegistrationBean<A2AAgentMachineAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    IamPersonalAccessTokenAuthenticationFilter iamPersonalAccessTokenAuthenticationFilter(
            AccessTokenCommandPort tokens,
            com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver clientIpResolver,
            Clock clock) {
        return new IamPersonalAccessTokenAuthenticationFilter(tokens, clientIpResolver, clock);
    }

    @Bean
    FilterRegistrationBean<IamPersonalAccessTokenAuthenticationFilter> disableIamPersonalAccessTokenAuthenticationFilterServletRegistration(
            IamPersonalAccessTokenAuthenticationFilter filter) {
        FilterRegistrationBean<IamPersonalAccessTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    R3HumanApiPermissionRegistry r3HumanApiPermissionRegistry() {
        return new R3HumanApiPermissionRegistry();
    }

    @Bean
    R3CompatibilityDecisionEvaluator r3CompatibilityDecisionEvaluator(
            IamApiRuntimeDao dao,
            TransactionTemplate iamRuntimeTransactionTemplate,
            Clock clock) {
        return new R3CompatibilityDecisionEvaluator(dao, iamRuntimeTransactionTemplate, clock);
    }

    @Bean
    R3HttpAuthorizationFilter r3HttpAuthorizationFilter(
            R3HumanApiPermissionRegistry registry,
            IamSecurityAdapter authorization,
            R3CompatibilityDecisionEvaluator compatibility) {
        return new R3HttpAuthorizationFilter(registry, authorization, compatibility);
    }

    @Bean
    FilterRegistrationBean<R3HttpAuthorizationFilter> disableR3HttpAuthorizationFilterServletRegistration(
            R3HttpAuthorizationFilter filter) {
        FilterRegistrationBean<R3HttpAuthorizationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    LegacyPasswordCredentialAdapter legacyPasswordCredentialAdapter(
            ObjectProvider<AdminIdentityRepository> legacyIdentities,
            ObjectProvider<PasswordEncoder> passwordEncoder,
            AuthenticationSubjectPort canonicalSubjects,
            IamApiRuntimeDao dao,
            TransactionTemplate iamRuntimeTransactionTemplate,
            LegacyCredentialAdapterProperties credentialAdapterProperties,
            Clock clock) {
        return new LegacyPasswordCredentialAdapter(
                legacyIdentities.getIfAvailable(), passwordEncoder.getIfAvailable(), canonicalSubjects, dao,
                iamRuntimeTransactionTemplate, credentialAdapterProperties, clock);
    }

    @Bean
    CanonicalCredentialBroker canonicalCredentialBroker(
            PasswordAuthenticationCommandPort passwords,
            LegacyPasswordCredentialAdapter legacyPasswords) {
        return new CanonicalCredentialBroker(passwords, legacyPasswords);
    }

    @Bean
    IamBootstrapApiPort iamBootstrapApiPort(
            RootBootstrapStateRepository states,
            IdentityCommandPort identities,
            IdentityQueryPort identityQueries,
            MfaAuthenticationCommandPort mfa,
            RootAuthenticationCommandPort rootAuthentication,
            SessionCommandPort sessions,
            BrowserSessionRepository sessionRepository,
            TenantCommandPort tenants,
            OrganizationQueryPort organizationQueries,
            RbacAdministrationPort rbac,
            AccessTokenCommandPort tokens,
            IamOneTimeSecretDeliveryPort delivery,
            IamIdempotencyExecutor idempotency,
            Clock clock) {
        return new IamBootstrapRuntimeOrchestrator(
                states, identities, identityQueries, mfa,
                rootAuthentication, sessions, sessionRepository,
                tenants, organizationQueries, rbac, tokens, delivery, idempotency, clock);
    }

    @Bean
    RootInstallationBootstrapper rootInstallationBootstrapper(
            IamRuntimeProperties properties,
            IdentityQueryPort identityQueries,
            IdentityCommandPort identityCommands,
            PasswordAuthenticationCommandPort passwords,
            RootBootstrapStateRepository bootstrapStates,
            PasswordCredentialRepository credentials,
            RootAuthenticationCommandPort rootAuthentication,
            AuthenticationEventPublisher events,
            IamApiRuntimeDao dao,
            TransactionTemplate iamRuntimeTransactionTemplate,
            Clock clock) {
        return new RootInstallationBootstrapper(
                properties, identityQueries, identityCommands, passwords, bootstrapStates, credentials,
                rootAuthentication, events, dao, iamRuntimeTransactionTemplate, clock);
    }

    @Bean
    SmartInitializingSingleton rootInstallationStartup(RootInstallationBootstrapper bootstrapper) {
        return bootstrapper::installIfRequired;
    }

    @Bean
    IamAuthenticationApiPort iamAuthenticationApiPort(
            PasswordAuthenticationCommandPort passwords,
            MfaAuthenticationCommandPort mfa,
            SessionCommandPort sessions,
            BrowserSessionRepository sessionRepository,
            SessionPolicyRepository sessionPolicies,
            PasswordCredentialRepository credentials,
            RootBootstrapStateRepository bootstrapStates,
            AccessTokenCommandPort tokens,
            IdentityQueryPort identities,
            IdentityCommandPort identityCommands,
            TenantCommandPort tenantCommands,
            IamAdministrationProjectionPort projections,
            IamApiRuntimeDao dao,
            IamLoginChallengeService challenges,
            IamOneTimeSecretDeliveryPort delivery,
            IamIdempotencyExecutor idempotency,
            TransactionTemplate iamRuntimeTransactionTemplate,
            IamRuntimeProperties properties,
            CanonicalCredentialBroker credentialBroker,
            LegacyPasswordCredentialAdapter legacyPasswords,
            IamEffectiveAccessQueryService effectiveAccess,
            IamFederationRuntimeOrchestrator federation,
            Clock clock) {
        return new IamAuthenticationRuntimeOrchestrator(
                passwords, mfa, sessions, sessionRepository, sessionPolicies, credentials, bootstrapStates,
                tokens, identities, identityCommands, tenantCommands, projections, dao, challenges, delivery, idempotency,
                iamRuntimeTransactionTemplate, properties, credentialBroker, legacyPasswords, effectiveAccess, federation, clock);
    }

    @Bean
    IamUserRuntimeOrchestrator iamUserRuntimeOrchestrator(
            IdentityCommandPort identities,
            TenantCommandPort tenants,
            AccessTokenCommandPort tokens,
            IamOneTimeSecretDeliveryPort delivery,
            IamIdempotencyExecutor idempotency) {
        return new IamUserRuntimeOrchestrator(identities, tenants, tokens, delivery, idempotency);
    }

    @Bean
    IamUserLifecycleRuntimeOrchestrator iamUserLifecycleRuntimeOrchestrator(
            IdentityCommandPort identities,
            IdentityQueryPort identityQueries,
            TenantCommandPort tenants,
            DepartmentCommandPort departments,
            GroupCommandPort groups,
            RbacAdministrationPort rbac,
            PasswordAuthenticationCommandPort passwords,
            AccessTokenCommandPort tokens,
            IamActivationDeliveryPort delivery,
            IamIdempotencyExecutor idempotency) {
        return new IamUserLifecycleRuntimeOrchestrator(
                identities, identityQueries, tenants, departments, groups, rbac, passwords, tokens, delivery, idempotency);
    }

    @Bean
    IamUserInvitationRuntimeOrchestrator iamUserInvitationRuntimeOrchestrator(
            IamAdministrationProjectionPort projections,
            AccessTokenCommandPort tokens,
            IamActivationDeliveryPort delivery,
            IamIdempotencyExecutor idempotency,
            Clock clock) {
        return new IamUserInvitationRuntimeOrchestrator(
                projections, tokens, delivery, idempotency, clock);
    }

    @Bean
    IamPlatformUserRuntimeOrchestrator iamPlatformUserRuntimeOrchestrator(
            IdentityCommandPort identities,
            PasswordCredentialRepository credentials,
            BrowserSessionRepository sessions,
            MfaMethodRepository mfaMethods,
            IamIdempotencyExecutor idempotency,
            DepartmentRepository departments,
            Clock clock) {
        return new IamPlatformUserRuntimeOrchestrator(identities, credentials, sessions, mfaMethods, idempotency, departments, clock);
    }

    @Bean
    IamSessionCredentialRuntimeOrchestrator iamSessionCredentialRuntimeOrchestrator(
            SessionCommandPort sessions,
            BrowserSessionRepository sessionRepository,
            MfaAuthenticationCommandPort mfa,
            PasswordAuthenticationCommandPort passwords,
            PasswordCredentialRepository passwordCredentials,
            AccessTokenCommandPort tokens,
            IamActivationDeliveryPort delivery,
            IamIdempotencyExecutor idempotency,
            IdentityQueryPort identityQueries,
            IdentityCommandPort identityCommands) {
        return new IamSessionCredentialRuntimeOrchestrator(
                sessions, sessionRepository, mfa, passwords, passwordCredentials, tokens, delivery, idempotency, identityQueries, identityCommands);
    }

    @Bean
    IamSecurityPolicyApiPort iamSecurityPolicyApiPort(
            IamApiRuntimeDao dao,
            IamTenantOrganizationDao tenantDao,
            IamTransactionalOutboxWriter outbox,
            IamIdempotencyExecutor idempotency,
            ObjectMapper objectMapper) {
        return new IamSecurityPolicyRuntimeOrchestrator(
                dao, tenantDao, outbox, idempotency, objectMapper);
    }

    private void requireConfigured(String property, String environmentName, String capability) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " is required when " + capability + " is enabled");
        }
    }

    private void requireEnabled(String property) {
        if (!environment.getProperty(property, Boolean.class, false)) {
            throw new IllegalStateException(
                    "IAM_API_COMPOSITION_INCOMPLETE: " + property + " must be true when aeg.iam.runtime.enabled=true");
        }
    }
}
