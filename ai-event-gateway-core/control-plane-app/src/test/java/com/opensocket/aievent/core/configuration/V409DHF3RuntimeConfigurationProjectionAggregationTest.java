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
import com.opensocket.aievent.core.action.AdapterActionMcpRuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.action.AdapterActionMcpRuntimeConfigurationView;
import com.opensocket.aievent.core.action.AdapterActionWorkerRuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.action.AdapterActionWorkerRuntimeConfigurationView;
import com.opensocket.aievent.core.configuration.distribution.RuntimeConfigurationSnapshotProjectionService;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolverRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationScope;
import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinition;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinitionStore;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationApplyStatus;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationNodeApplyState;
import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationEmergencyOverride;
import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationConfigSet;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevision;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionItem;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionState;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionStore;

class V409DHF3RuntimeConfigurationProjectionAggregationTest {
    private static final String SET_KEY = "RUNTIME/ADAPTER_ACTION/SYSTEM";
    private static final String SET_ID = "adapter-action-system";
    private static final String ACTIVE_REVISION = "revision-7";
    private static final String PENDING_REVISION = "revision-8";
    private static final String WORKER_KEY = "adapter-actions.worker.retry-enabled";
    private static final String MCP_KEY = "adapter-actions.mcp.enabled";
    private static final String WORKER_MAX_KEY = "adapter-actions.worker.max-attempts";

    @Test
    void sharedConfigSetCountsNodesApprovalsAndRecentRevisionOnlyOnce() {
        Fixture f = fixture();

        RuntimeConfigurationAdminService.Overview overview = f.service.overview();
        RuntimeConfigurationAdminService.GovernanceOverview governance = f.service.governance();

        assertThat(overview.knownNodes()).isEqualTo(3);
        assertThat(overview.appliedNodes()).isEqualTo(3);
        assertThat(overview.pendingApproval()).isEqualTo(1);
        assertThat(overview.recentChanges()).hasSize(1);
        assertThat(overview.recentChanges().getFirst().revisionId()).isEqualTo(ACTIVE_REVISION);
        assertThat(overview.recentChanges().getFirst().configSetKey()).isEqualTo(SET_KEY);
        assertThat(overview.recentChanges().getFirst().category()).contains("Adapter Action Worker", "Adapter Action MCP");
        assertThat(governance.pendingRevisions()).hasSize(1);
        assertThat(governance.activeEmergencyOverrides()).hasSize(1);
    }

    @Test
    void categoryLifecycleIsProjectedFromPerKeyRuntimeAuthority() {
        Fixture f = fixture();
        RuntimeConfigurationAdminService.Overview migrated = f.service.overview();
        assertThat(category(migrated, "adapter-action-worker").lifecycle()).isEqualTo("MIGRATED");
        assertThat(category(migrated, "adapter-action-mcp").lifecycle()).isEqualTo("MIGRATED");

        f.authority.replace(Set.of(WORKER_KEY, MCP_KEY));
        RuntimeConfigurationAdminService.Overview mixedAuthority = f.service.overview();
        assertThat(category(mixedAuthority, "adapter-action-worker").lifecycle()).isEqualTo("MIXED");
        assertThat(category(mixedAuthority, "adapter-action-mcp").lifecycle()).isEqualTo("MIGRATED");
    }

    private static RuntimeConfigurationAdminService.Category category(RuntimeConfigurationAdminService.Overview overview, String id) {
        return overview.categories().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
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
        AdapterActionWorkerRuntimeConfigurationView worker = mock(AdapterActionWorkerRuntimeConfigurationView.class);
        AdapterActionMcpRuntimeConfigurationView mcp = mock(AdapterActionMcpRuntimeConfigurationView.class);
        RuntimeConfigurationEffectiveValueResolverRegistry effectiveValues =
                new RuntimeConfigurationEffectiveValueResolverRegistry(List.of(
                        new AdapterActionWorkerRuntimeConfigurationEffectiveValueResolver(worker),
                        new AdapterActionMcpRuntimeConfigurationEffectiveValueResolver(mcp)));

        when(definitions.listMigrationAuthorized()).thenReturn(List.of(workerDefinition(), workerMaxDefinition(), mcpDefinition()));
        RuntimeConfigurationConfigSet set = new RuntimeConfigurationConfigSet(SET_ID, SET_KEY, OpenDispatchEnvironment.DEV,
                ConfigurationScope.COMPONENT, "SYSTEM", "ADAPTER_ACTION", "ACTIVE", 1L, now(), now());
        when(revisions.findConfigSetBySetKey(OpenDispatchEnvironment.DEV, SET_KEY)).thenReturn(Optional.of(set));
        when(revisions.findActiveRevisionId(SET_ID)).thenReturn(Optional.of(ACTIVE_REVISION));
        when(revisions.listItems(ACTIVE_REVISION)).thenReturn(List.of(item(WORKER_KEY, "true"), item(WORKER_MAX_KEY, "3"), item(MCP_KEY, "true")));
        when(revisions.listRevisions(SET_ID, 20)).thenReturn(List.of(publishedRevision(), pendingRevision()));
        when(revisions.listRevisions(SET_ID, 50)).thenReturn(List.of(publishedRevision(), pendingRevision()));
        when(snapshots.desiredPayloadHash(SET_ID)).thenReturn("fingerprint");
        when(distribution.listRequiredApplyStates(SET_ID)).thenReturn(List.of(
                applied("core-1"), applied("core-2"), applied("core-3")));
        when(localValues.hasSnapshot(SET_KEY)).thenReturn(true);
        when(localValues.keys(SET_KEY)).thenReturn(Set.of(WORKER_KEY, WORKER_MAX_KEY, MCP_KEY));
        when(localValues.revisionId(SET_KEY)).thenReturn(Optional.of(ACTIVE_REVISION));
        when(worker.retryEnabled()).thenReturn(true);
        when(worker.maxAttempts()).thenReturn(3);
        when(mcp.enabled()).thenReturn(true);
        when(governance.listActiveEmergencyOverrides(SET_ID)).thenReturn(List.of(new RuntimeConfigurationEmergencyOverride(
                "override-1", SET_ID, WORKER_KEY, ACTIVE_REVISION, "false", "hash", "ACTIVE", "incident",
                "operator-a", now(), now().plusMinutes(10), null, null, null)));
        authority.activate(Set.of(WORKER_KEY, WORKER_MAX_KEY, MCP_KEY));

        RuntimeConfigurationAdminService service = new RuntimeConfigurationAdminService(revisions, distribution,
                revisionService, governance, definitions, snapshots, localValues, authority, effectiveValues,
                new ObjectMapper(), "DEV");
        return new Fixture(service, authority);
    }

    private static RuntimeConfigurationDefinition workerDefinition() {
        return definition(WORKER_KEY, "Adapter worker retry enabled", "adapter-action-worker");
    }

    private static RuntimeConfigurationDefinition workerMaxDefinition() {
        return new RuntimeConfigurationDefinition(WORKER_MAX_KEY, "Adapter worker maximum attempts", "ADAPTER_ACTION", "ADAPTER_ACTION",
                "RUNTIME_CONFIG_DB", "COMPONENT", "ADAPTER_ACTION", "INTEGER", "attempts", "MEDIUM",
                "HOT_NEXT_CYCLE", "RUNTIME_SNAPSHOT", SET_KEY, true, true, "{\"minimum\":1,\"maximum\":20}", "{}", "V40_9D",
                "{\"categoryId\":\"adapter-action-worker\",\"description\":\"Maximum attempts.\",\"recommended\":\"3\",\"effect\":\"Next action\",\"impactPositive\":\"Resilience.\",\"impactTradeoff\":\"More retries.\"}",
                "APPROVED", true, 1, "test");
    }

    private static RuntimeConfigurationDefinition mcpDefinition() {
        return definition(MCP_KEY, "MCP adapter action enabled", "adapter-action-mcp");
    }

    private static RuntimeConfigurationDefinition definition(String key, String displayName, String categoryId) {
        return new RuntimeConfigurationDefinition(key, displayName, "ADAPTER_ACTION", "ADAPTER_ACTION",
                "RUNTIME_CONFIG_DB", "COMPONENT", "ADAPTER_ACTION", "BOOLEAN", "boolean", "MEDIUM",
                "HOT_IMMEDIATE", "RUNTIME_SNAPSHOT", SET_KEY, true, true, "{}", "{}", "V40_9D",
                "{\"categoryId\":\"" + categoryId + "\",\"description\":\"Runtime setting.\",\"recommended\":\"Enabled\",\"effect\":\"Next cycle\",\"impactPositive\":\"Operational control.\",\"impactTradeoff\":\"Requires governance.\"}",
                "APPROVED", true, 1, "test");
    }

    private static RuntimeConfigurationRevisionItem item(String key, String json) {
        return new RuntimeConfigurationRevisionItem(ACTIVE_REVISION, key, json, "hash", "operator-a", now(), "operator-a", now());
    }

    private static RuntimeConfigurationRevision publishedRevision() {
        return revision(ACTIVE_REVISION, 7L, RuntimeConfigurationRevisionState.PUBLISHED, "operator-publisher", now());
    }

    private static RuntimeConfigurationRevision pendingRevision() {
        return revision(PENDING_REVISION, 8L, RuntimeConfigurationRevisionState.PENDING_APPROVAL, null, null);
    }

    private static RuntimeConfigurationRevision revision(String id, long sequence, RuntimeConfigurationRevisionState state, String publishedBy, OffsetDateTime publishedAt) {
        OffsetDateTime created = now().minusMinutes(5);
        return new RuntimeConfigurationRevision(id, SET_ID, sequence, ACTIVE_REVISION, null, null, state, 1,
                "test revision", "operator-a", created, "operator-a", created.plusMinutes(1), "operator-a", created.plusMinutes(2),
                state == RuntimeConfigurationRevisionState.PENDING_APPROVAL ? null : "operator-b",
                state == RuntimeConfigurationRevisionState.PENDING_APPROVAL ? null : created.plusMinutes(3),
                publishedBy, publishedAt, "operator-a", now());
    }

    private static RuntimeConfigurationNodeApplyState applied(String nodeId) {
        return new RuntimeConfigurationNodeApplyState(SET_ID, nodeId, "CORE", nodeId + "-instance", ACTIVE_REVISION,
                ACTIVE_REVISION, RuntimeConfigurationApplyStatus.APPLIED, "fingerprint", now(), now(), now(), now(), null, null, null, null, null);
    }

    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    private record Fixture(RuntimeConfigurationAdminService service, RuntimeConfigurationAuthorityRegistry authority) {}
}
