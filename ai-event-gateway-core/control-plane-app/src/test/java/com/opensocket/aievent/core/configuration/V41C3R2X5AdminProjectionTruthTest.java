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

/** X5 contract: Admin projection reports runtime-target coverage and independent truth dimensions. */
class V41C3R2X5AdminProjectionTruthTest {
    private static final String KEY="core.lifecycle.incident.max-batch-size";
    private static final String SET_KEY="RUNTIME/INCIDENT/SYSTEM";
    private static final String SET_ID="incident-system";
    private static final String REV="rev-11";

    @Test
    void dualReadSnapshotIsReportedAsSnapshotEffectiveNotStartupFallback() {
        Fixture f=fixture(List.of(runtimeDefinition(KEY,true,"MIGRATION_READY"),domainDefinition()));
        when(f.revisions.listItems(REV)).thenReturn(List.of(item("100")));
        when(f.localValues.hasSnapshot(SET_KEY)).thenReturn(true);
        when(f.localValues.keys(SET_KEY)).thenReturn(Set.of(KEY));
        when(f.localValues.revisionId(SET_KEY)).thenReturn(Optional.of(REV));
        when(f.localValues.canonicalJsonValue(SET_KEY,KEY)).thenReturn(Optional.of("100"));

        RuntimeConfigurationAdminService.SettingDetail detail=f.service.detail(KEY);

        assertThat(detail.migrationState()).isEqualTo("MIGRATION_READY");
        assertThat(detail.authorityMode()).isEqualTo("DUAL_READ");
        assertThat(detail.effectiveSource()).isEqualTo("LOCAL_RUNTIME_SNAPSHOT");
        assertThat(detail.applicationStatus()).isNotEqualTo("STARTUP_FALLBACK");
        assertThat(detail.configSetKey()).isEqualTo(SET_KEY);
        assertThat(detail.configSetId()).isEqualTo(SET_ID);
    }

    @Test
    void runtimeMigrationCoverageExcludesNonRuntimeDefinitionsAndShowsProposedTargetsReadOnly() {
        RuntimeConfigurationDefinition ready=runtimeDefinition(KEY,true,"MIGRATION_READY");
        RuntimeConfigurationDefinition proposed=runtimeDefinition("core.lifecycle.incident.future-policy",false,"PROPOSED");
        Fixture f=fixture(List.of(ready,proposed,domainDefinition()));
        when(f.revisions.listItems(REV)).thenReturn(List.of(item("100")));

        RuntimeConfigurationAdminService.Overview overview=f.service.overview();

        assertThat(overview.migrationCoverage().runtimeTargetDefinitions()).isEqualTo(2);
        assertThat(overview.migrationCoverage().migrationAuthorized()).isEqualTo(1);
        assertThat(overview.migrationCoverage().pendingConsumerMigration()).isEqualTo(1);
        assertThat(overview.categories().stream().flatMap(category->category.settings().stream())
                .anyMatch(setting->"core.lifecycle.incident.future-policy".equals(setting.key()))).isTrue();
        RuntimeConfigurationAdminService.SettingDetail proposedDetail=f.service.detail("core.lifecycle.incident.future-policy");
        assertThat(proposedDetail.migrationState()).isEqualTo("PROPOSED");
        assertThat(proposedDetail.authorityMode()).isEqualTo("STARTUP_ONLY");
        assertThat(proposedDetail.editable()).isFalse();
        assertThat(proposedDetail.applicationStatus()).isEqualTo("NOT_MIGRATED");
    }

    private static Fixture fixture(List<RuntimeConfigurationDefinition> defs){
        RuntimeConfigurationRevisionStore revisions=mock(RuntimeConfigurationRevisionStore.class);
        RuntimeConfigurationDistributionStore distribution=mock(RuntimeConfigurationDistributionStore.class);
        RuntimeConfigurationRevisionService revisionService=mock(RuntimeConfigurationRevisionService.class);
        RuntimeConfigurationGovernanceStore governance=mock(RuntimeConfigurationGovernanceStore.class);
        RuntimeConfigurationDefinitionStore definitions=mock(RuntimeConfigurationDefinitionStore.class);
        RuntimeConfigurationSnapshotProjectionService projections=mock(RuntimeConfigurationSnapshotProjectionService.class);
        RuntimeConfigurationSnapshotValues localValues=mock(RuntimeConfigurationSnapshotValues.class);
        RuntimeConfigurationAuthorityRegistry authority=new RuntimeConfigurationAuthorityRegistry();
        RuntimeConfigurationEffectiveValueResolverRegistry effectiveValues=new RuntimeConfigurationEffectiveValueResolverRegistry(List.of());
        when(definitions.listAll()).thenReturn(defs);
        when(definitions.listMigrationAuthorized()).thenReturn(defs.stream().filter(RuntimeConfigurationDefinition::migrationAuthorized).toList());
        RuntimeConfigurationConfigSet set=new RuntimeConfigurationConfigSet(SET_ID,SET_KEY,OpenDispatchEnvironment.DEV,
                ConfigurationScope.COMPONENT,"SYSTEM","INCIDENT","ACTIVE",1L,OffsetDateTime.now(ZoneOffset.UTC),OffsetDateTime.now(ZoneOffset.UTC));
        when(revisions.findConfigSetBySetKey(OpenDispatchEnvironment.DEV,SET_KEY)).thenReturn(Optional.of(set));
        when(revisions.findActiveRevisionId(SET_ID)).thenReturn(Optional.of(REV));
        when(distribution.listRequiredApplyStates(SET_ID)).thenReturn(List.of());
        when(projections.desiredSnapshotFingerprint(SET_ID)).thenReturn("fp");
        RuntimeConfigurationAdminService service=new RuntimeConfigurationAdminService(revisions,distribution,revisionService,governance,
                definitions,projections,localValues,authority,effectiveValues,new ObjectMapper(),"DEV");
        return new Fixture(service,revisions,localValues);
    }

    private static RuntimeConfigurationDefinition runtimeDefinition(String key,boolean authorized,String review){
        return new RuntimeConfigurationDefinition(key,key,"CORE","CORE","RUNTIME_TUNABLE","COMPONENT","SYSTEM","INTEGER","count","MEDIUM",
                "HOT_NEXT_CYCLE","IncidentRuntimeConfigurationView",SET_KEY,true,authorized,"{\"minimum\":1,\"maximum\":5000}","{}","V41_C3R2X5",
                "{\"categoryId\":\"incident-recovery\",\"categoryDisplayName\":\"Incident & Recovery\",\"description\":\"Incident runtime setting.\",\"recommended\":\"100\",\"effect\":\"Next cycle\",\"impactPositive\":\"Controls runtime processing.\",\"impactTradeoff\":\"May change processing load.\"}",
                review,authorized,1,"test");
    }

    private static RuntimeConfigurationDefinition domainDefinition(){
        return new RuntimeConfigurationDefinition("core.observability.include-tenant-tag","tenant tag","CORE","CORE","DOMAIN_CONFIG","COMPONENT","SYSTEM","BOOLEAN","none","LOW",
                "RESTART_REQUIRED","CoreProperties",null,false,false,"{}","{}","V41_C3R2X5",
                "{\"categoryId\":\"core-domain\",\"description\":\"Non-runtime domain config.\",\"recommended\":\"false\",\"effect\":\"Restart\",\"impactPositive\":\"N/A\",\"impactTradeoff\":\"N/A\"}",
                "RETIRED",false,1,"test");
    }

    private static RuntimeConfigurationRevisionItem item(String json){
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        return new RuntimeConfigurationRevisionItem(REV,KEY,json,"hash","tester",now,"tester",now);
    }

    private record Fixture(RuntimeConfigurationAdminService service,RuntimeConfigurationRevisionStore revisions,RuntimeConfigurationSnapshotValues localValues){}
}
