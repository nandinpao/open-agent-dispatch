package com.opensocket.aievent.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.action.executor.AdapterExecutorRuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.action.executor.AdapterExecutorRuntimeConfigurationView;
import com.opensocket.aievent.core.configuration.distribution.RuntimeConfigurationSnapshotProjectionService;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolverRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationScope;
import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinition;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinitionStore;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;
import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationConfigSet;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionItem;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionStore;

class V409DHF2RuntimeConfigurationAdminTruthTest {
    private static final String KEY = "adapter-executor.circuit-breaker.failure-threshold";
    private static final String SET_KEY = "RUNTIME/ADAPTER_EXECUTION/SYSTEM";
    private static final String SET_ID = "adapter-execution-system";
    private static final String REVISION = "revision-2";

    @Test
    void migratedMissingDesiredKeyNeverProjectsStartupAsApplied() {
        Fixture f = fixture();
        f.authority.activate(Set.of(KEY));
        when(f.revisions.listItems(REVISION)).thenReturn(List.of());
        when(f.localValues.hasSnapshot(SET_KEY)).thenReturn(false);
        when(f.adapter.circuitBreakerFailureThreshold()).thenReturn(5);

        RuntimeConfigurationAdminService.SettingDetail detail = f.service.detail(KEY);

        assertThat(detail.authoritySource()).isEqualTo("RUNTIME_CONFIGURATION");
        assertThat(detail.valuePresentInActiveRevision()).isFalse();
        assertThat(detail.localEffectiveValue()).isNull();
        assertThat(detail.desiredValue()).isNull();
        assertThat(detail.configurationState()).isEqualTo("INCOMPLETE");
        assertThat(detail.applicationStatus()).isEqualTo("CONFIGURATION_INCOMPLETE");
        assertThat(detail.errorCode()).isEqualTo("CONFIGURATION_INCOMPLETE");
    }

    @Test
    void activeRevisionDoesNotImplyRuntimeAuthorityBeforeCutover() {
        Fixture f = fixture();
        when(f.revisions.listItems(REVISION)).thenReturn(List.of(item("3")));
        when(f.localValues.hasSnapshot(SET_KEY)).thenReturn(false);
        when(f.adapter.circuitBreakerFailureThreshold()).thenReturn(5);

        RuntimeConfigurationAdminService.SettingDetail detail = f.service.detail(KEY);

        assertThat(detail.authoritySource()).isEqualTo("STARTUP_FALLBACK");
        assertThat(detail.authority()).contains("Dual read");
        assertThat(detail.migrationState()).isEqualTo("MIGRATION_READY");
        assertThat(detail.authorityMode()).isEqualTo("DUAL_READ");
        assertThat(detail.localEffectiveValue()).isEqualTo(5);
        assertThat(detail.desiredValue()).isEqualTo(3);
        assertThat(detail.valuePresentInActiveRevision()).isTrue();
        assertThat(detail.applicationStatus()).isEqualTo("STARTUP_FALLBACK");
    }

    @Test
    void migratedLocalRevisionLagIsProjectedAsDrift() {
        Fixture f = fixture();
        f.authority.activate(Set.of(KEY));
        when(f.revisions.listItems(REVISION)).thenReturn(List.of(item("3")));
        when(f.localValues.hasSnapshot(SET_KEY)).thenReturn(true);
        when(f.localValues.keys(SET_KEY)).thenReturn(Set.of(KEY));
        when(f.localValues.revisionId(SET_KEY)).thenReturn(Optional.of("revision-1"));
        when(f.adapter.circuitBreakerFailureThreshold()).thenReturn(2);

        RuntimeConfigurationAdminService.SettingDetail detail = f.service.detail(KEY);

        assertThat(detail.configurationState()).isEqualTo("DRIFT");
        assertThat(detail.effectiveSource()).isEqualTo("LOCAL_RUNTIME_SNAPSHOT");
        assertThat(detail.desiredRevisionId()).isEqualTo(REVISION);
        assertThat(detail.localAppliedRevisionId()).isEqualTo("revision-1");
        assertThat(detail.localEffectiveValue()).isEqualTo(2);
        assertThat(detail.desiredValue()).isEqualTo(3);
    }

    private static Fixture fixture() {
        RuntimeConfigurationRevisionStore revisions = mock(RuntimeConfigurationRevisionStore.class);
        RuntimeConfigurationDistributionStore distribution = mock(RuntimeConfigurationDistributionStore.class);
        RuntimeConfigurationRevisionService revisionService = mock(RuntimeConfigurationRevisionService.class);
        RuntimeConfigurationGovernanceStore governance = mock(RuntimeConfigurationGovernanceStore.class);
        RuntimeConfigurationDefinitionStore definitions = mock(RuntimeConfigurationDefinitionStore.class);
        RuntimeConfigurationSnapshotProjectionService snapshots = mock(RuntimeConfigurationSnapshotProjectionService.class);
        RuntimeConfigurationSnapshotValues localValues = mock(RuntimeConfigurationSnapshotValues.class);
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        AdapterExecutorRuntimeConfigurationView adapter = mock(AdapterExecutorRuntimeConfigurationView.class);
        RuntimeConfigurationEffectiveValueResolverRegistry effectiveValues =
                new RuntimeConfigurationEffectiveValueResolverRegistry(List.of(
                        new AdapterExecutorRuntimeConfigurationEffectiveValueResolver(adapter)));

        when(definitions.listMigrationAuthorized()).thenReturn(List.of(definition()));
        RuntimeConfigurationConfigSet set = new RuntimeConfigurationConfigSet(SET_ID, SET_KEY, OpenDispatchEnvironment.DEV,
                ConfigurationScope.COMPONENT, "SYSTEM", "ADAPTER_EXECUTION", "ACTIVE", 1L,
                OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC));
        when(revisions.findConfigSetBySetKey(OpenDispatchEnvironment.DEV, SET_KEY)).thenReturn(Optional.of(set));
        when(revisions.findActiveRevisionId(SET_ID)).thenReturn(Optional.of(REVISION));
        when(distribution.listRequiredApplyStates(SET_ID)).thenReturn(List.of());
        when(snapshots.desiredPayloadHash(SET_ID)).thenReturn("fingerprint");
        when(localValues.revisionId(SET_KEY)).thenReturn(Optional.empty());

        RuntimeConfigurationAdminService service = new RuntimeConfigurationAdminService(revisions, distribution,
                revisionService, governance, definitions, snapshots, localValues, authority, effectiveValues,
                new ObjectMapper(), "DEV");
        return new Fixture(service, revisions, localValues, authority, adapter);
    }

    private static RuntimeConfigurationDefinition definition() {
        return new RuntimeConfigurationDefinition(KEY, "Circuit breaker failure threshold", "ADAPTER_EXECUTION",
                "ADAPTER_EXECUTION", "RUNTIME_CONFIG_DB", "COMPONENT", "SYSTEM", "INTEGER", "count", "MEDIUM",
                "HOT_IMMEDIATE", "AdapterExecutorRuntimeConfigurationView", SET_KEY, true, true,
                "{\"minimum\":1,\"maximum\":100}", "{}", "V40_9D",
                "{\"categoryId\":\"adapter-execution\",\"description\":\"Failures before opening.\",\"recommended\":\"5\",\"effect\":\"Subsequent failures\",\"impactPositive\":\"Protects downstream systems.\",\"impactTradeoff\":\"Lower values open sooner.\"}",
                "APPROVED", true, 1, "test");
    }

    private static RuntimeConfigurationRevisionItem item(String json) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new RuntimeConfigurationRevisionItem(REVISION, KEY, json, "hash", "tester", now, "tester", now);
    }

    private record Fixture(RuntimeConfigurationAdminService service, RuntimeConfigurationRevisionStore revisions,
            RuntimeConfigurationSnapshotValues localValues, RuntimeConfigurationAuthorityRegistry authority,
            AdapterExecutorRuntimeConfigurationView adapter) {}
}
