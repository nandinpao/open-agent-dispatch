package com.opensocket.aievent.core.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.configuration.distribution.RuntimeConfigurationDistributionProperties;
import com.opensocket.aievent.core.configuration.distribution.RuntimeConfigurationSnapshotProjectionService;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationInventoryGovernanceStatus;
import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverPlan;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverState;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverStore;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveStore;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinition;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinitionStore;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationApplyStatus;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationNodeApplyState;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationRequiredNodeTarget;
import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernance;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationConfigSet;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionItem;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionStore;

import tools.jackson.databind.ObjectMapper;

/**
 * V41 C3R2-X4 generic two-phase cutover engine.
 *
 * <p>PREPARE does not change migration governance. It persists a RUNTIME_ONLY cutover intent,
 * causing signed v2 snapshots to carry the final authority manifest while governance remains
 * MIGRATION_READY. Required nodes must ACK that exact authority fingerprint before FINALIZE may
 * atomically commit MIGRATED. Local JVM authority is reconciled only AFTER_COMMIT.</p>
 */
@Service
public class RuntimeConfigurationGenericCutoverService {
    private static final Logger log=LoggerFactory.getLogger(RuntimeConfigurationGenericCutoverService.class);
    private static final int REQUIRED_CONTRACT_VERSION=2;

    private final RuntimeConfigurationRevisionStore revisions;
    private final RuntimeConfigurationDefinitionStore definitions;
    private final ConfigurationInventoryGovernanceStore governance;
    private final RuntimeConfigurationGovernanceStore emergencyGovernance;
    private final RuntimeConfigurationDistributionStore distribution;
    private final RuntimeConfigurationSnapshotProjectionService projections;
    private final RuntimeConfigurationDistributionProperties distributionProperties;
    private final RuntimeConfigurationCutoverStore cutovers;
    private final RuntimeConfigurationCutoverWaveStore cutoverWaves;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;
    private final OpenDispatchEnvironment environment;

    public RuntimeConfigurationGenericCutoverService(RuntimeConfigurationRevisionStore revisions,
            RuntimeConfigurationDefinitionStore definitions,ConfigurationInventoryGovernanceStore governance,
            RuntimeConfigurationGovernanceStore emergencyGovernance,RuntimeConfigurationDistributionStore distribution,
            RuntimeConfigurationSnapshotProjectionService projections,RuntimeConfigurationDistributionProperties distributionProperties,
            RuntimeConfigurationCutoverStore cutovers,RuntimeConfigurationCutoverWaveStore cutoverWaves,ApplicationEventPublisher events,ObjectMapper json,
            @Value("${opendispatch.environment}") String environment){
        this.revisions=revisions;this.definitions=definitions;this.governance=governance;this.emergencyGovernance=emergencyGovernance;
        this.distribution=distribution;this.projections=projections;this.distributionProperties=distributionProperties;
        this.cutovers=cutovers;this.cutoverWaves=cutoverWaves;this.events=events;this.json=json;this.environment=OpenDispatchEnvironment.parseCanonical(environment);
    }

    @Transactional(readOnly=true)
    public CutoverStatus status(String setKey){return evaluate(required(setKey,"setKey"));}
    @Transactional(readOnly=true)
    public CutoverStatus statusByConfigSetId(String configSetId){return status(setKeyByConfigSetId(configSetId));}
    public CutoverStatus prepareByConfigSetId(String configSetId,String actor,String reason,String correlationId){return prepare(setKeyByConfigSetId(configSetId),actor,reason,correlationId);}
    public CutoverStatus finalizeByConfigSetId(String configSetId,String actor,String reason,String correlationId){return finalizeCutover(setKeyByConfigSetId(configSetId),actor,reason,correlationId);}
    public CutoverStatus cancelByConfigSetId(String configSetId,String actor,String reason,String correlationId){return cancel(setKeyByConfigSetId(configSetId),actor,reason,correlationId);}

    @Transactional
    public CutoverStatus prepare(String setKey,String actor,String reason,String correlationId){
        assertDirectMutationAllowed(setKey);
        return prepareWaveManaged(setKey,actor,reason,correlationId);
    }

    CutoverStatus prepareWaveManaged(String setKey,String actor,String reason,String correlationId){
        String key=required(setKey,"setKey"),operator=required(actor,"actor"),why=required(reason,"reason");
        Context ctx=context(key);
        var existing=cutovers.findPrepared(ctx.set().configSetId());
        if(existing.isPresent()) return evaluate(key);
        CutoverStatus before=evaluate(key);
        if("ALREADY_MIGRATED".equals(before.phase())) return before;
        if(!before.blockers().isEmpty()) throw new IllegalStateException("CONFIGURATION_CUTOVER_NOT_READY setKey="+key+" blockers="+before.blockers());

        String targetFingerprint=fingerprintFor(ctx.projection().payloadHash(),"RUNTIME_ONLY",ctx.keys());
        RuntimeConfigurationCutoverPlan plan=new RuntimeConfigurationCutoverPlan(UUID.randomUUID().toString(),ctx.set().configSetId(),key,
                environment.name(),ctx.activeRevisionId(),REQUIRED_CONTRACT_VERSION,"RUNTIME_ONLY",ctx.keys(),targetFingerprint,
                RuntimeConfigurationCutoverState.PREPARED,operator,why,OffsetDateTime.now(ZoneOffset.UTC),null,null,1L);
        cutovers.createPrepared(plan);
        distribution.requestRedistribution(ctx.set().configSetId(),ctx.activeRevisionId(),"GENERIC_CUTOVER_PREPARED cutoverId="+plan.cutoverId()+" correlationId="+text(correlationId));
        log.info("runtime_config_generic_cutover_prepared setKey={} configSetId={} revisionId={} cutoverId={} targetFingerprint={} requiredKeys={} actor={} correlationId={}",
                key,ctx.set().configSetId(),ctx.activeRevisionId(),plan.cutoverId(),targetFingerprint,ctx.keys().size(),operator,text(correlationId));
        return evaluate(key);
    }

    @Transactional
    public CutoverStatus finalizeCutover(String setKey,String actor,String reason,String correlationId){
        assertDirectMutationAllowed(setKey);
        return finalizeWaveManaged(setKey,actor,reason,correlationId);
    }

    CutoverStatus finalizeWaveManaged(String setKey,String actor,String reason,String correlationId){
        String key=required(setKey,"setKey"),operator=required(actor,"actor"),why=required(reason,"reason");
        Context ctx=context(key);
        RuntimeConfigurationCutoverPlan plan=cutovers.findPrepared(ctx.set().configSetId()).orElse(null);
        if(plan==null){
            CutoverStatus current=evaluate(key);
            if("ALREADY_MIGRATED".equals(current.phase())) return current;
            throw new IllegalStateException("CONFIGURATION_CUTOVER_PREPARED_PLAN_REQUIRED setKey="+key);
        }
        CutoverStatus ready=evaluate(key);
        if(!"READY_TO_FINALIZE".equals(ready.phase()))
            throw new IllegalStateException("CONFIGURATION_CUTOVER_NOT_FINALIZABLE setKey="+key+" phase="+ready.phase()+" blockers="+ready.blockers());
        if(!Objects.equals(plan.revisionId(),ctx.activeRevisionId()))
            throw new IllegalStateException("CONFIGURATION_CUTOVER_ACTIVE_REVISION_CHANGED plan="+plan.revisionId()+" active="+ctx.activeRevisionId());

        for(String definitionKey:ctx.keys()){
            ConfigurationInventoryGovernance current=governance.findGovernance(definitionKey)
                    .orElseThrow(()->new IllegalStateException("CONFIGURATION_CUTOVER_GOVERNANCE_MISSING key="+definitionKey));
            if(current.status()==ConfigurationInventoryGovernanceStatus.MIGRATED||current.status()==ConfigurationInventoryGovernanceStatus.LEGACY_RETIRED)continue;
            if(current.status()!=ConfigurationInventoryGovernanceStatus.MIGRATION_READY)
                throw new IllegalStateException("CONFIGURATION_CUTOVER_STATE_CONFLICT key="+definitionKey+" state="+current.status());
            if(operator.equals(current.migrationAuthorizedBy()))
                throw new IllegalStateException("CONFIGURATION_GOVERNANCE_SOD_MIGRATION_FINALIZE key="+definitionKey);
            ConfigurationInventoryGovernance migrated=new ConfigurationInventoryGovernance(current.configurationKey(),ConfigurationInventoryGovernanceStatus.MIGRATED,
                    current.sourceObservationHash(),current.domainOwner(),current.authorityClass(),current.scope(),current.risk(),current.mutability(),current.consumerContract(),
                    current.adminEditable(),current.requiresApproval(),current.classifiedBy(),current.classifiedAt(),current.ownerReviewedBy(),current.ownerReviewedAt(),
                    current.architectureApprovedBy(),current.architectureApprovedAt(),current.migrationAuthorizedBy(),current.migrationAuthorizedAt(),why,current.version(),current.updatedAt());
            governance.save(migrated,current.version(),"MIGRATED",operator,why,auditDetail(plan,correlationId));
        }
        cutovers.markFinalized(plan.cutoverId(),plan.version(),operator,why);
        events.publishEvent(new RuntimeConfigurationAuthorityChangedEvent(key,plan.cutoverId()));
        log.info("runtime_config_generic_cutover_finalized setKey={} cutoverId={} revisionId={} fingerprint={} actor={} correlationId={}",
                key,plan.cutoverId(),plan.revisionId(),plan.expectedSnapshotFingerprint(),operator,text(correlationId));
        return evaluate(key);
    }

    @Transactional
    public CutoverStatus cancel(String setKey,String actor,String reason,String correlationId){
        assertDirectMutationAllowed(setKey);
        return cancelWaveManaged(setKey,actor,reason,correlationId);
    }

    CutoverStatus cancelWaveManaged(String setKey,String actor,String reason,String correlationId){
        String key=required(setKey,"setKey"),operator=required(actor,"actor"),why=required(reason,"reason");
        Context ctx=context(key);
        RuntimeConfigurationCutoverPlan plan=cutovers.findPrepared(ctx.set().configSetId())
                .orElseThrow(()->new IllegalStateException("CONFIGURATION_CUTOVER_PREPARED_PLAN_REQUIRED setKey="+key));
        boolean anyMigrated=ctx.keys().stream().map(k->governance.findGovernance(k).map(ConfigurationInventoryGovernance::status).orElse(ConfigurationInventoryGovernanceStatus.DISCOVERED))
                .anyMatch(s->s==ConfigurationInventoryGovernanceStatus.MIGRATED||s==ConfigurationInventoryGovernanceStatus.LEGACY_RETIRED);
        boolean anyReady=ctx.keys().stream().map(k->governance.findGovernance(k).map(ConfigurationInventoryGovernance::status).orElse(ConfigurationInventoryGovernanceStatus.DISCOVERED))
                .anyMatch(s->s==ConfigurationInventoryGovernanceStatus.MIGRATION_READY);
        if(anyMigrated&&!anyReady)throw new IllegalStateException("CONFIGURATION_CUTOVER_ALREADY_COMMITTED setKey="+key);
        cutovers.markCancelled(plan.cutoverId(),plan.version(),operator,why);
        distribution.requestRedistribution(ctx.set().configSetId(),ctx.activeRevisionId(),"GENERIC_CUTOVER_CANCELLED cutoverId="+plan.cutoverId()+" correlationId="+text(correlationId));
        log.warn("runtime_config_generic_cutover_cancelled setKey={} cutoverId={} actor={} correlationId={}",key,plan.cutoverId(),operator,text(correlationId));
        return evaluate(key);
    }


    private void assertDirectMutationAllowed(String setKey){
        String key=required(setKey,"setKey");
        cutoverWaves.findBySetKey(key).ifPresent(wave->{
            throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_MANAGED_USE_WAVE_API setKey="+key+" waveId="+wave.waveId());
        });
    }

    private CutoverStatus evaluate(String setKey){
        Context ctx=context(setKey);
        List<String> blockers=new ArrayList<>();
        RuntimeConfigurationCutoverPlan prepared=cutovers.findPrepared(ctx.set().configSetId()).orElse(null);
        var latest=cutovers.findLatest(ctx.set().configSetId()).orElse(null);

        boolean allMigrated=true;
        List<KeyStatus> keyStatuses=new ArrayList<>();
        for(RuntimeConfigurationDefinition definition:ctx.definitions()){
            ConfigurationInventoryGovernance g=governance.findGovernance(definition.key()).orElse(null);
            String status=g==null?"MISSING":g.status().name();
            keyStatuses.add(new KeyStatus(definition.key(),status,definition.consumerContract()));
            if(g==null){blockers.add("GOVERNANCE_MISSING:"+definition.key());allMigrated=false;continue;}
            if(g.status()!=ConfigurationInventoryGovernanceStatus.MIGRATED&&g.status()!=ConfigurationInventoryGovernanceStatus.LEGACY_RETIRED) allMigrated=false;
            if(g.status()!=ConfigurationInventoryGovernanceStatus.MIGRATION_READY&&g.status()!=ConfigurationInventoryGovernanceStatus.MIGRATED&&g.status()!=ConfigurationInventoryGovernanceStatus.LEGACY_RETIRED)
                blockers.add("KEY_NOT_MIGRATION_READY:"+definition.key()+":"+g.status());
            if(blank(definition.consumerContract())) blockers.add("CONSUMER_CONTRACT_MISSING:"+definition.key());
        }
        if(distributionProperties.authorityContractVersion()<REQUIRED_CONTRACT_VERSION) blockers.add("CONTROL_PLANE_AUTHORITY_CONTRACT_V2_DISABLED");
        if(!ctx.activeKeys().containsAll(ctx.keys())){LinkedHashSet<String> missing=new LinkedHashSet<>(ctx.keys());missing.removeAll(ctx.activeKeys());blockers.add("ACTIVE_REVISION_INCOMPLETE:"+missing);}
        if(!ctx.keys().containsAll(ctx.activeKeys())){LinkedHashSet<String> extra=new LinkedHashSet<>(ctx.activeKeys());extra.removeAll(ctx.keys());blockers.add("ACTIVE_REVISION_NON_RUNTIME_KEYS:"+extra);}
        if(!emergencyGovernance.listActiveEmergencyOverrides(ctx.set().configSetId()).isEmpty()) blockers.add("ACTIVE_EMERGENCY_OVERRIDE");

        List<RuntimeConfigurationRequiredNodeTarget> targets=distribution.listRequiredTargets(ctx.set().configSetId());
        if(targets.isEmpty()) blockers.add("REQUIRED_NODE_TARGETS_MISSING");
        List<NodeStatus> nodes=new ArrayList<>();
        for(RuntimeConfigurationRequiredNodeTarget target:targets){
            if(target.supportedAuthorityContractVersion()<REQUIRED_CONTRACT_VERSION)
                blockers.add("NODE_AUTHORITY_CONTRACT_V2_UNSUPPORTED:"+target.nodeId()+":v"+target.supportedAuthorityContractVersion());
        }

        String expectedFingerprint=prepared==null?ctx.projection().snapshotFingerprint(REQUIRED_CONTRACT_VERSION):prepared.expectedSnapshotFingerprint();
        List<RuntimeConfigurationNodeApplyState> states=distribution.listRequiredApplyStates(ctx.set().configSetId());
        boolean criticalAuthorityHealth=cutoverWaves.findBySetKey(setKey).map(w->"CRITICAL".equals(w.riskTier())).orElse(false);
        int applied=0;
        for(RuntimeConfigurationNodeApplyState state:states){
            boolean remote="GATEWAY".equals(state.nodeRole())||"WORKER".equals(state.nodeRole());
            boolean authorityHealthy=!criticalAuthorityHealth||!remote||"ACTIVE".equals(state.authorityRuntimeState());
            boolean exact=state.state()==RuntimeConfigurationApplyStatus.APPLIED&&Objects.equals(ctx.activeRevisionId(),state.desiredRevisionId())
                    &&Objects.equals(ctx.activeRevisionId(),state.appliedRevisionId())&&expectedFingerprint.equalsIgnoreCase(text(state.snapshotFingerprint()))
                    &&authorityHealthy;
            if(!authorityHealthy) blockers.add("NODE_AUTHORITY_NOT_ACTIVE:"+state.nodeId()+":"+(state.authorityRuntimeState()==null?"MISSING":state.authorityRuntimeState()));
            if(exact)applied++; else blockers.add("NODE_NOT_CONVERGED:"+state.nodeId()+":"+state.state());
            RuntimeConfigurationRequiredNodeTarget target=targets.stream().filter(t->t.nodeId().equals(state.nodeId())).findFirst().orElse(null);
            nodes.add(new NodeStatus(state.nodeId(),state.nodeRole(),target==null?1:target.supportedAuthorityContractVersion(),state.state().name(),
                    state.appliedRevisionId(),state.snapshotFingerprint(),state.authorityRuntimeState(),state.snapshotExpiresAt(),state.authorityObservedAt(),exact));
        }
        if(states.size()!=targets.size()) blockers.add("REQUIRED_NODE_APPLY_STATE_DENOMINATOR_MISMATCH");

        String phase;
        if(allMigrated) phase=blockers.isEmpty()?"ALREADY_MIGRATED":"MIGRATED_WITH_DRIFT";
        else if(prepared==null) phase=blockers.isEmpty()?"READY_TO_PREPARE":"NOT_READY";
        else phase=blockers.isEmpty()?"READY_TO_FINALIZE":"PREPARED_AWAITING_CONVERGENCE";
        String cutoverId=prepared!=null?prepared.cutoverId():(latest==null?null:latest.cutoverId());
        return new CutoverStatus(setKey,ctx.set().configSetId(),environment.name(),ctx.activeRevisionId(),phase,
                cutoverId,expectedFingerprint,ctx.keys(),List.copyOf(blockers),keyStatuses,nodes,targets.size(),applied);
    }

    private String setKeyByConfigSetId(String configSetId){return revisions.findConfigSet(required(configSetId,"configSetId"))
            .map(RuntimeConfigurationConfigSet::setKey).orElseThrow(()->new IllegalStateException("CONFIGURATION_CUTOVER_CONFIG_SET_REQUIRED configSetId="+configSetId));}

    private Context context(String setKey){
        RuntimeConfigurationConfigSet set=revisions.findConfigSetBySetKey(environment,setKey)
                .orElseThrow(()->new IllegalStateException("CONFIGURATION_CUTOVER_CONFIG_SET_REQUIRED setKey="+setKey));
        String active=revisions.findActiveRevisionId(set.configSetId())
                .orElseThrow(()->new IllegalStateException("CONFIGURATION_CUTOVER_ACTIVE_REVISION_REQUIRED setKey="+setKey));
        List<RuntimeConfigurationDefinition> scoped=definitions.listMigrationAuthorized().stream().filter(d->setKey.equals(d.configSetKey())).toList();
        if(scoped.isEmpty())throw new IllegalStateException("CONFIGURATION_CUTOVER_DEFINITIONS_MISSING setKey="+setKey);
        Set<String> keys=scoped.stream().map(RuntimeConfigurationDefinition::key).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> activeKeys=revisions.listItems(active).stream().map(RuntimeConfigurationRevisionItem::definitionKey).collect(java.util.stream.Collectors.toSet());
        return new Context(set,active,scoped,Set.copyOf(keys),Set.copyOf(activeKeys),projections.desiredProjection(set.configSetId()));
    }

    private String auditDetail(RuntimeConfigurationCutoverPlan plan,String correlationId){
        try{return json.writeValueAsString(java.util.Map.of("stage","V41_C3R2X4_GENERIC_CUTOVER","cutoverId",plan.cutoverId(),"setKey",plan.setKey(),
                "revisionId",plan.revisionId(),"snapshotFingerprint",plan.expectedSnapshotFingerprint(),"correlationId",text(correlationId)));}
        catch(Exception ex){throw new IllegalStateException("CONFIGURATION_CUTOVER_AUDIT_SERIALIZATION_FAILED",ex);}
    }
    static String fingerprintFor(String payloadHash,String authorityMode,Set<String> requiredKeys){return sha256(required(payloadHash,"payloadHash")+"\n"+required(authorityMode,"authorityMode")+"\n"+String.join(",",new TreeSet<>(requiredKeys)));}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private static boolean blank(String value){return value==null||value.isBlank();}
    private static String required(String value,String field){if(blank(value))throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static String text(String value){return value==null?"":value.trim();}

    private record Context(RuntimeConfigurationConfigSet set,String activeRevisionId,List<RuntimeConfigurationDefinition> definitions,
            Set<String> keys,Set<String> activeKeys,RuntimeConfigurationSnapshotProjectionService.SnapshotProjection projection){}
    public record KeyStatus(String key,String governanceStatus,String consumerContract){}
    public record NodeStatus(String nodeId,String nodeRole,int supportedAuthorityContractVersion,String applyState,String appliedRevisionId,String snapshotFingerprint,
            String authorityRuntimeState,OffsetDateTime snapshotExpiresAt,OffsetDateTime authorityObservedAt,boolean converged){}
    public record CutoverStatus(String setKey,String configSetId,String environment,String activeRevisionId,String phase,String cutoverId,
            String expectedSnapshotFingerprint,Set<String> requiredKeys,List<String> blockers,List<KeyStatus> keys,List<NodeStatus> nodes,int requiredNodeCount,int convergedNodeCount){}
}
