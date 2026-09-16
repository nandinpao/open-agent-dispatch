package com.opensocket.aievent.core.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import com.opensocket.aievent.core.integration.IntegrationEventProperties;
import com.opensocket.aievent.core.iam.runtime.config.EventIntakeSecurityProperties;
import com.opensocket.aievent.core.security.CoreInternalSecurityProperties;

class CoreDeploymentModeValidatorTest {

    @Test
    void shouldRejectMockExecutorInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getMock().setEnabled(true);

        CoreDeploymentModeValidator validator = validator(adapter, "prod");

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectMockIssueVendorInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setDefaultVendor("MOCK");

        CoreDeploymentModeValidator validator = validator(adapter, "prod");

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }


    @Test
    void shouldRejectMemoryStoreInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        MockEnvironment environment = productionEnvironmentWithPersistentStores();
        environment.setProperty("dispatch.request-store", "MEMORY");

        CoreDeploymentModeValidator validator = validator(adapter, environment);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldAllowPersistentStoresInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores());

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectDisabledInternalSecurityInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        CoreInternalSecurityProperties security = productionSecurity();
        security.setEnabled(false);

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores(), security);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectPlaceholderInternalTokenInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        CoreInternalSecurityProperties security = productionSecurity();
        security.setGatewayToken("change-me");

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores(), security);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }



    @Test
    void shouldRejectAngleBracketInternalTokenInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        CoreInternalSecurityProperties security = productionSecurity();
        security.setGatewayToken("<change-me>");

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores(), security);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectPlaceholderDispatchInternalTokenInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        DispatchProperties dispatch = productionDispatchProperties();
        dispatch.getClient().setInternalToken("<change-me>");

        CoreDeploymentModeValidator validator = validator(
                adapter,
                productionEnvironmentWithPersistentStores(),
                productionSecurity(),
                dispatch,
                productionRecoveryGovernance());

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectLocalhostDispatchGatewayEndpointInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        DispatchProperties dispatch = productionDispatchProperties();
        dispatch.getClient().setDefaultGatewayBaseUrl("http://localhost:18081");

        CoreDeploymentModeValidator validator = validator(
                adapter,
                productionEnvironmentWithPersistentStores(),
                productionSecurity(),
                dispatch,
                productionRecoveryGovernance());

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectLegacyInternalTokenHeaderInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        CoreInternalSecurityProperties security = productionSecurity();
        security.setAllowLegacyTokenHeader(true);

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores(), security);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectDisabledScopedIssueIdentityInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setScopedIdentityEnabled(false);

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores());

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectOptionalScopedIssueIdentityInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setScopedIdentityRequired(false);

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores());

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldAllowScopedIssueExecutionWithoutFixedProviderCredentialsInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setDefaultVendor("REDMINE");
        adapter.getIssue().setScopedIdentityEnabled(true);
        adapter.getIssue().setScopedIdentityRequired(true);

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores());

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectDisabledRecoveryGovernanceInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        RecoveryGovernanceProperties recoveryGovernance = productionRecoveryGovernance();
        recoveryGovernance.setEnabled(false);

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores(), productionSecurity(), recoveryGovernance);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectSelfApprovalAllowedForHighRiskRecoveryInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        RecoveryGovernanceProperties recoveryGovernance = productionRecoveryGovernance();
        recoveryGovernance.setForbidSelfApproval(false);

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores(), productionSecurity(), recoveryGovernance);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectLegacyOnlyEventIntakeInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        EventIntakeSecurityProperties eventIntake = productionEventIntakeSecurity();
        eventIntake.setMode(EventIntakeSecurityProperties.Mode.LEGACY_ONLY);

        CoreDeploymentModeValidator validator = validator(
                adapter, productionEnvironmentWithPersistentStores(), productionSecurity(),
                productionDispatchProperties(), productionRecoveryGovernance(), eventIntake);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectSharedEventIntakeAndOperatorTokenInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        CoreInternalSecurityProperties security = productionSecurity();
        security.setEventIntakeToken(security.getOperatorToken());

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores(), security);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectSharedEventIntakeAndClusterTokenInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        CoreInternalSecurityProperties security = productionSecurity();
        security.setEventIntakeToken("cluster-token-123");
        MockEnvironment environment = productionEnvironmentWithPersistentStores();
        environment.setProperty("CLUSTER_INTERNAL_TOKEN", "cluster-token-123");

        CoreDeploymentModeValidator validator = validator(adapter, environment, security);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldAllowJwtRequiredWithoutLegacyEventIntakeTokenInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        CoreInternalSecurityProperties security = productionSecurity();
        security.setEventIntakeToken("");

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores(), security);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldAllowExplicitMockOnlyOutsideProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getMock().setEnabled(true);
        adapter.getIssue().setDefaultVendor("MOCK");
        adapter.getIssue().setJiraMockEnabled(true);

        CoreDeploymentModeValidator validator = validator(adapter, "local");

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    private CoreDeploymentModeValidator validator(AdapterActionExecutionProperties adapter, String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return validator(adapter, environment);
    }

    private CoreDeploymentModeValidator validator(AdapterActionExecutionProperties adapter, MockEnvironment environment) {
        return validator(adapter, environment, productionSecurity());
    }

    private CoreDeploymentModeValidator validator(AdapterActionExecutionProperties adapter,
                                                  MockEnvironment environment,
                                                  CoreInternalSecurityProperties security) {
        return validator(adapter, environment, security, productionDispatchProperties(), productionRecoveryGovernance());
    }

    private CoreDeploymentModeValidator validator(AdapterActionExecutionProperties adapter,
                                                  MockEnvironment environment,
                                                  CoreInternalSecurityProperties security,
                                                  RecoveryGovernanceProperties recoveryGovernance) {
        return validator(adapter, environment, security, productionDispatchProperties(), recoveryGovernance);
    }

    private CoreDeploymentModeValidator validator(AdapterActionExecutionProperties adapter,
                                                  MockEnvironment environment,
                                                  CoreInternalSecurityProperties security,
                                                  DispatchProperties dispatchProperties,
                                                  RecoveryGovernanceProperties recoveryGovernance) {
        return validator(adapter, environment, security, dispatchProperties, recoveryGovernance, productionEventIntakeSecurity());
    }

    private CoreDeploymentModeValidator validator(AdapterActionExecutionProperties adapter,
                                                  MockEnvironment environment,
                                                  CoreInternalSecurityProperties security,
                                                  DispatchProperties dispatchProperties,
                                                  RecoveryGovernanceProperties recoveryGovernance,
                                                  EventIntakeSecurityProperties eventIntakeSecurity) {
        return new CoreDeploymentModeValidator(
                new CoreDeploymentProperties(),
                adapter,
                new IntegrationEventProperties(),
                eventIntakeSecurity,
                security,
                dispatchProperties,
                recoveryGovernance,
                environment);
    }

    private CoreInternalSecurityProperties productionSecurity() {
        CoreInternalSecurityProperties security = new CoreInternalSecurityProperties();
        security.setEnabled(true);
        security.setProtectApiMutations(true);
        security.setPermitActuatorHealthInfo(false);
        security.setAuditLogEnabled(true);
        security.setAllowLegacyTokenHeader(false);
        security.setGatewayToken("gateway-token-123");
        security.setAdapterWorkerToken("adapter-token-123");
        security.setEventIntakeToken("event-intake-token-123");
        security.setOperatorToken("operator-token-123");
        security.setRecoveryOperatorToken("recovery-operator-token-123");
        security.setRecoveryAdminToken("recovery-admin-token-123");
        security.setRecoveryApproverToken("recovery-approver-token-123");
        security.setActuatorToken("actuator-token-123");
        return security;
    }


    private EventIntakeSecurityProperties productionEventIntakeSecurity() {
        EventIntakeSecurityProperties properties = new EventIntakeSecurityProperties();
        properties.setMode(EventIntakeSecurityProperties.Mode.JWT_REQUIRED);
        properties.setAuditEnabled(true);
        return properties;
    }


    private DispatchProperties productionDispatchProperties() {
        DispatchProperties dispatch = new DispatchProperties();
        dispatch.getClient().setEnabled(true);
        dispatch.getClient().setInternalToken("dispatch-token-123");
        dispatch.getClient().setDefaultGatewayBaseUrl("https://gateway.prod.internal");
        dispatch.getClient().getGatewayBaseUrls().put("gateway-prod-001", "https://gateway-prod-001.internal");
        return dispatch;
    }

    private RecoveryGovernanceProperties productionRecoveryGovernance() {
        RecoveryGovernanceProperties recoveryGovernance = new RecoveryGovernanceProperties();
        recoveryGovernance.setEnabled(true);
        recoveryGovernance.setRequireReason(true);
        recoveryGovernance.setMinReasonLength(12);
        recoveryGovernance.setRequireConfirmation(true);
        recoveryGovernance.setAllowBodyOperatorIdOverride(false);
        recoveryGovernance.setRequireDualControlForHighRisk(true);
        recoveryGovernance.setForbidSelfApproval(true);
        return recoveryGovernance;
    }

    private MockEnvironment productionEnvironmentWithPersistentStores() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("core.outbox.store", "MYBATIS");
        environment.setProperty("core.integration-events.store", "MYBATIS");
        environment.setProperty("event.decisions.store", "MYBATIS");
        environment.setProperty("event.dedup.store", "MYBATIS");
        environment.setProperty("event.dedup.cache-store", "REDISSON");
        environment.setProperty("event.dedup.snapshot-store", "MYBATIS");
        environment.setProperty("incident.store", "MYBATIS");
        environment.setProperty("incident.summary.store", "MYBATIS");
        environment.setProperty("task.store", "MYBATIS");
        environment.setProperty("task.callback.store", "MYBATIS");
        environment.setProperty("gateway-nodes.store", "MYBATIS");
        environment.setProperty("agent-directory.store", "MYBATIS");
        environment.setProperty("assignment.store", "MYBATIS");
        environment.setProperty("routing.decision-store", "MYBATIS");
        environment.setProperty("dispatch.request-store", "MYBATIS");
        environment.setProperty("adapter-actions.store", "MYBATIS");
        environment.setProperty("adapter-executor.audit.store", "MYBATIS");
        return environment;
    }
}
