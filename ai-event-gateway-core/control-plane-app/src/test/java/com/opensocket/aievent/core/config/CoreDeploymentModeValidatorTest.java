package com.opensocket.aievent.core.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import com.opensocket.aievent.core.integration.IntegrationEventProperties;
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
    void shouldRejectRedmineDefaultVendorWithoutRealExecutorInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setDefaultVendor("REDMINE");

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores());

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectPlaceholderRedmineApiKeyInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setDefaultVendor("REDMINE");
        adapter.getIssue().getRedmine().setEnabled(true);
        adapter.getIssue().getRedmine().setBaseUrl("https://redmine.prod.internal");
        adapter.getIssue().getRedmine().setApiKey("<change-me>");
        adapter.getIssue().getRedmine().setProjectId("MES-OPS");

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores());

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectLocalGitlabEndpointInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setDefaultVendor("GITLAB");
        adapter.getIssue().getGitlab().setEnabled(true);
        adapter.getIssue().getGitlab().setBaseUrl("http://127.0.0.1:8080");
        adapter.getIssue().getGitlab().setPrivateToken("gitlab-prod-token-123");
        adapter.getIssue().getGitlab().setProjectId("group/project");

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores());

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectPreEncodedGitlabProjectIdInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setDefaultVendor("GITLAB");
        adapter.getIssue().getGitlab().setEnabled(true);
        adapter.getIssue().getGitlab().setBaseUrl("https://gitlab.prod.internal");
        adapter.getIssue().getGitlab().setPrivateToken("gitlab-prod-token-123");
        adapter.getIssue().getGitlab().setProjectId("group%2Fproject");

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores());

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectJiraDefaultVendorUntilRealJiraExecutorProfileExistsInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setDefaultVendor("JIRA");

        CoreDeploymentModeValidator validator = validator(adapter, productionEnvironmentWithPersistentStores());

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldAllowRealRedmineIssueExecutorInProdProfile() {
        AdapterActionExecutionProperties adapter = new AdapterActionExecutionProperties();
        adapter.getIssue().setDefaultVendor("REDMINE");
        adapter.getIssue().getRedmine().setEnabled(true);
        adapter.getIssue().getRedmine().setBaseUrl("https://redmine.prod.internal");
        adapter.getIssue().getRedmine().setApiKey("redmine-prod-token-123");
        adapter.getIssue().getRedmine().setProjectId("MES-OPS");

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
        return new CoreDeploymentModeValidator(
                new CoreDeploymentProperties(),
                adapter,
                new IntegrationEventProperties(),
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
        security.setOperatorToken("operator-token-123");
        security.setRecoveryOperatorToken("recovery-operator-token-123");
        security.setRecoveryAdminToken("recovery-admin-token-123");
        security.setRecoveryApproverToken("recovery-approver-token-123");
        security.setActuatorToken("actuator-token-123");
        return security;
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
