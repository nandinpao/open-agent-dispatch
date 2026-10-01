package com.opensocket.aievent.core.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.opensocket.aievent.core.kernel.configuration.revision.ConfigurationRevisionConflictException;
import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationEmergencyOverride;
import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationConfigSet;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevision;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionItem;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionState;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionStore;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator-facing projection for the governed runtime settings migrated through V40-8.
 *
 * <p>This is deliberately not a generic key/value editor. Source-controlled definitions and
 * typed runtime views remain authoritative. V40-7 will replace the temporary platform
 * permission bridge with dedicated configuration permissions and approval policy.</p>
 */
@Service
public class RuntimeConfigurationAdminService {
    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigurationAdminService.class);
    public static final String TEMPORARY_READ_PERMISSION = "permission.entry_point.read";
    public static final String TEMPORARY_MANAGE_PERMISSION = "permission.entry_point.manage";

    private static final String DISPATCH_SET = "RUNTIME/DISPATCH/SYSTEM";
    private static final String ADAPTER_SET = "RUNTIME/ADAPTER_EXECUTION/SYSTEM";
    private static final String ADAPTER_ACTION_SET = "RUNTIME/ADAPTER_ACTION/SYSTEM";
    private static final String ISSUE_SET = "RUNTIME/ISSUE/SYSTEM";
    private static final String TASK_SET = "RUNTIME/TASK/SYSTEM";
    private static final String AGENT_REMEDIATION_SET = "RUNTIME/AGENT_REMEDIATION/SYSTEM";

    private final RuntimeConfigurationRevisionStore revisions;
    private final RuntimeConfigurationDistributionStore distribution;
    private final RuntimeConfigurationRevisionService revisionService;
    private final RuntimeConfigurationGovernanceStore governance;
    private final RuntimeConfigurationDefinitionStore definitions;
    private final RuntimeConfigurationSnapshotProjectionService snapshotProjections;
    private final RuntimeConfigurationSnapshotValues localSnapshotValues;
    private final RuntimeConfigurationAuthorityRegistry authorityRegistry;
    private final RuntimeConfigurationEffectiveValueResolverRegistry effectiveValueResolvers;
    private final ObjectMapper json;
    private final OpenDispatchEnvironment environment;
    private final Map<String, SettingMeta> settings;
    private final List<CategoryMeta> categories;

    public RuntimeConfigurationAdminService(
            RuntimeConfigurationRevisionStore revisions,
            RuntimeConfigurationDistributionStore distribution,
            RuntimeConfigurationRevisionService revisionService,
            RuntimeConfigurationGovernanceStore governance,
            RuntimeConfigurationDefinitionStore definitions,
            RuntimeConfigurationSnapshotProjectionService snapshotProjections,
            RuntimeConfigurationSnapshotValues localSnapshotValues,
            RuntimeConfigurationAuthorityRegistry authorityRegistry,
            RuntimeConfigurationEffectiveValueResolverRegistry effectiveValueResolvers,
            ObjectMapper json,
            @Value("${opendispatch.environment}") String environment) {
        this.revisions = revisions;
        this.distribution = distribution;
        this.revisionService = revisionService;
        this.governance = governance;
        this.definitions = definitions;
        this.snapshotProjections = snapshotProjections;
        this.localSnapshotValues = localSnapshotValues;
        this.authorityRegistry = authorityRegistry;
        this.effectiveValueResolvers = effectiveValueResolvers;
        this.json = json;
        this.environment = OpenDispatchEnvironment.parseCanonical(environment);
        this.settings = loadSettings(definitions);
        this.categories = loadCategories(definitions);
        Set<String> snapshotBackedKeys = this.settings.values().stream()
                .filter(meta -> meta.setKey() != null && !meta.setKey().isBlank())
                .map(SettingMeta::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.effectiveValueResolvers.requireProjectionCoverage(this.settings.keySet(), snapshotBackedKeys);
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        List<Category> categoryViews = new ArrayList<>();
        Map<String, SetStatus> setStatuses = new LinkedHashMap<>();
        Set<String> uniqueSetKeys = uniqueSetKeys();
        int applying = 0;
        int partial = 0;
        int failed = 0;
        int applyEntries = 0;
        int appliedEntries = 0;

        for (String setKey : uniqueSetKeys) {
            SetStatus status = setStatus(setKey);
            setStatuses.put(setKey, status);
            if ("PUBLISHED_APPLYING".equals(status.applicationStatus())) applying++;
            if ("PARTIALLY_APPLIED".equals(status.applicationStatus())) partial++;
            if ("FAILED".equals(status.applicationStatus())) failed++;
            applyEntries += status.knownNodes();
            appliedEntries += status.appliedNodes();
        }

        Set<String> pendingRevisionIds = new LinkedHashSet<>();
        Map<String, RecentChange> recentByRevision = new LinkedHashMap<>();
        for (String setKey : uniqueSetKeys) {
            Optional<RuntimeConfigurationConfigSet> set = revisions.findConfigSetBySetKey(environment, setKey);
            if (set.isEmpty()) continue;
            for (RuntimeConfigurationRevision revision : revisions.listRevisions(set.get().configSetId(), 20)) {
                if (revision.state() == RuntimeConfigurationRevisionState.PENDING_APPROVAL) pendingRevisionIds.add(revision.revisionId());
                if (revision.state() == RuntimeConfigurationRevisionState.PUBLISHED || revision.state() == RuntimeConfigurationRevisionState.SUPERSEDED) {
                    recentByRevision.putIfAbsent(revision.revisionId(), new RecentChange(setLabel(setKey), setKey, revision.revisionId(), revision.sequenceNo(), revision.state().name(), revision.reason(), revision.publishedBy(), revision.publishedAt()));
                }
            }
        }

        for (CategoryMeta category : categories) {
            List<SettingSummary> settingSummaries = settings.values().stream()
                .filter(meta -> Objects.equals(meta.categoryId(), category.id()))
                .map(meta -> summary(meta.key()))
                .toList();
            SetStatus setStatus = category.migrationAuthorizedCount()==0 ? new SetStatus("NOT_MIGRATED",0,0) : aggregateSetStatus(category.setKeys(), setStatuses);
            categoryViews.add(new Category(category.id(), category.title(), category.description(), categoryLifecycle(category), setStatus.applicationStatus(), settingSummaries,
                    category.definitionCount(), category.migrationAuthorizedCount(), category.editableCount(), List.copyOf(category.setKeys()), category.editableCount()==0));
        }

        List<RecentChange> recent = new ArrayList<>(recentByRevision.values());
        recent.sort(Comparator.comparing(RecentChange::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        if (recent.size() > 8) recent = new ArrayList<>(recent.subList(0, 8));
        String health = failed > 0 || partial > 0 ? "ACTION_REQUIRED" : applying > 0 ? "APPLYING" : "HEALTHY";
        MigrationCoverage migrationCoverage = migrationCoverage();
        return new Overview(environment.name(), environmentLabel(environment), health, applying + partial + failed, appliedEntries, applyEntries, pendingRevisionIds.size(), migrationCoverage, categoryViews, List.copyOf(recent),
            "Runtime target coverage is source-controlled independently from migration authorization. PROPOSED definitions are visible for closure planning but are not editable or publishable until a typed consumer is migrated and reviewed.");
    }

    @Transactional(readOnly = true)
    public SettingDetail detail(String key) {
        SettingMeta meta = requireSetting(key);
        Optional<RuntimeConfigurationConfigSet> set = revisions.findConfigSetBySetKey(environment, meta.setKey());
        String desiredRevisionId = set.flatMap(s -> revisions.findActiveRevisionId(s.configSetId())).orElse(null);
        Optional<RuntimeConfigurationRevisionItem> desiredItem = desiredRevisionId == null ? Optional.empty()
                : revisions.listItems(desiredRevisionId).stream().filter(item -> item.definitionKey().equals(meta.key())).findFirst();
        boolean valuePresentInActiveRevision = desiredItem.isPresent();
        Object desiredValue = desiredItem.map(this::decode).orElse(null);

        boolean runtimeAuthoritative = authorityRegistry.isRuntimeAuthoritative(meta.key());
        boolean hasConfigSetKey = meta.setKey()!=null && !meta.setKey().isBlank();
        boolean localSnapshotPresent = hasConfigSetKey && localSnapshotValues.hasSnapshot(meta.setKey());
        String localAppliedRevisionId = hasConfigSetKey ? localSnapshotValues.revisionId(meta.setKey()).orElse(null) : null;
        boolean localKeyPresent = false;
        String localSnapshotReadError = null;
        if (localSnapshotPresent) {
            try { localKeyPresent = localSnapshotValues.keys(meta.setKey()).contains(meta.key()); }
            catch (RuntimeException ex) { localSnapshotReadError = ex.getMessage(); }
        }
        SetStatus convergence = setStatus(meta.setKey());

        Object localEffectiveValue = null;
        String effectiveSource = runtimeAuthoritative ? "NONE" : "STARTUP_FALLBACK";
        String configurationState = "HEALTHY";
        String errorCode = null;
        String errorDetail = null;

        if (runtimeAuthoritative) {
            if (desiredRevisionId == null || !valuePresentInActiveRevision) {
                configurationState = "INCOMPLETE";
                errorCode = "CONFIGURATION_INCOMPLETE";
                errorDetail = desiredRevisionId == null
                        ? "No active Runtime Configuration revision is available for this migrated key."
                        : "The active Runtime Configuration revision does not contain this migrated key.";
            } else if (!localSnapshotPresent || localSnapshotReadError != null || !localKeyPresent) {
                configurationState = "INCOMPLETE";
                errorCode = "CONFIGURATION_INCOMPLETE";
                errorDetail = !localSnapshotPresent
                        ? "No authenticated local Runtime Configuration snapshot is applied for this migrated key."
                        : localSnapshotReadError != null
                                ? "The authenticated local Runtime Configuration snapshot cannot be read: " + localSnapshotReadError
                                : "The authenticated local Runtime Configuration snapshot does not contain this migrated key.";
            } else {
                effectiveSource = "LOCAL_RUNTIME_SNAPSHOT";
                try {
                    localEffectiveValue = runtimeValue(meta);
                    if (!Objects.equals(desiredRevisionId, localAppliedRevisionId)) configurationState = "DRIFT";
                } catch (RuntimeException ex) {
                    configurationState = "INCOMPLETE";
                    errorCode = "CONFIGURATION_INCOMPLETE";
                    errorDetail = ex.getMessage();
                    effectiveSource = "NONE";
                }
            }
        } else if (meta.migrationAuthorized()) {
            try {
                localEffectiveValue = runtimeValue(meta);
                if (localSnapshotPresent && localKeyPresent) effectiveSource = "LOCAL_RUNTIME_SNAPSHOT";
            } catch (RuntimeException ex) {
                configurationState = "FAILED";
                errorCode = "EFFECTIVE_CONFIGURATION_UNAVAILABLE";
                errorDetail = ex.getMessage();
                effectiveSource = "NONE";
            }
        } else {
            effectiveSource = "STARTUP_FALLBACK";
            localEffectiveValue = null;
        }

        String migrationState = meta.migrationAuthorized() ? (runtimeAuthoritative ? "MIGRATED" : "MIGRATION_READY") : normalizeMigrationState(meta.reviewStatus());
        String authorityMode = runtimeAuthoritative ? "RUNTIME_ONLY" : meta.migrationAuthorized() ? "DUAL_READ" : "STARTUP_ONLY";
        String applicationStatus;
        if ("INCOMPLETE".equals(configurationState)) applicationStatus = "CONFIGURATION_INCOMPLETE";
        else if ("FAILED".equals(configurationState)) applicationStatus = "FAILED";
        else if (!meta.migrationAuthorized()) applicationStatus = "NOT_MIGRATED";
        else if (localSnapshotPresent && localKeyPresent && "LOCAL_RUNTIME_SNAPSHOT".equals(effectiveSource)) applicationStatus = convergence.applicationStatus();
        else applicationStatus = "STARTUP_FALLBACK";

        if ("INCOMPLETE".equals(configurationState)) {
            log.warn("runtime_config_projection_incomplete key={} setKey={} desiredRevisionId={} localAppliedRevisionId={} activeValuePresent={} localSnapshotPresent={} localKeyPresent={} errorCode={}",
                    meta.key(), meta.setKey(), desiredRevisionId, localAppliedRevisionId, valuePresentInActiveRevision,
                    localSnapshotPresent, localKeyPresent, errorCode);
        } else if ("DRIFT".equals(configurationState)) {
            log.warn("runtime_config_projection_drift key={} setKey={} desiredRevisionId={} localAppliedRevisionId={} clusterStatus={}",
                    meta.key(), meta.setKey(), desiredRevisionId, localAppliedRevisionId, convergence.applicationStatus());
        } else {
            log.debug("runtime_config_projection_healthy key={} setKey={} authority={} desiredRevisionId={} localAppliedRevisionId={} clusterStatus={}",
                    meta.key(), meta.setKey(), runtimeAuthoritative ? "RUNTIME_CONFIGURATION" : "STARTUP_FALLBACK",
                    desiredRevisionId, localAppliedRevisionId, convergence.applicationStatus());
        }

        String authoritySource = runtimeAuthoritative ? "RUNTIME_CONFIGURATION" : "STARTUP_FALLBACK";
        String authorityLabel = switch (authorityMode) {
            case "RUNTIME_ONLY" -> "Runtime Configuration";
            case "DUAL_READ" -> "Dual read — Runtime snapshot preferred, startup fallback permitted";
            default -> "Startup controlled — runtime migration not yet authorized";
        };
        String configSetId = set.map(RuntimeConfigurationConfigSet::configSetId).orElse(null);
        return new SettingDetail(
            meta.categoryId(), meta.key(), meta.label(), meta.description(), meta.recommended(), meta.unit(), meta.dataType(), meta.risk(),
            meta.effect(), meta.impactPositive(), meta.impactTradeoff(), localEffectiveValue, localEffectiveValue, desiredValue,
            valuePresentInActiveRevision, authoritySource, effectiveSource, configurationState, convergence.applicationStatus(),
            migrationState, authorityMode, applicationStatus, authorityLabel, meta.owner(), meta.scope(), meta.mutability(), meta.setKey(), configSetId,
            desiredRevisionId, desiredRevisionId, localAppliedRevisionId, convergence.appliedNodes(), convergence.knownNodes(),
            meta.editable() && meta.migrationAuthorized(), meta.validation(), errorCode, errorDetail,
            "Migration state, authority mode, effective source and apply state are independent dimensions. Runtime-only authority never falls back to startup YAML/ENV when required runtime configuration is incomplete."
        );
    }

    @Transactional
    public RevisionView requestChange(String key, Object requestedValue, String expectedBaseRevisionId, String reason, String actor, String correlationId) {
        SettingMeta meta=requireEditableSetting(key);
        log.info("runtime_config_change_request_started key={} setKey={} expectedBaseRevisionId={} actor={} correlationId={}",
                meta.key(), meta.setKey(), text(expectedBaseRevisionId), actor, text(correlationId));
        String normalizedReason=required(reason,"reason");
        Object normalized=normalizeValue(meta,requestedValue);
        Optional<RuntimeConfigurationConfigSet> existingSet=revisions.findConfigSetBySetKey(environment,meta.setKey());
        String currentActive=existingSet.flatMap(v->revisions.findActiveRevisionId(v.configSetId())).orElse(null);
        if(!same(currentActive,text(expectedBaseRevisionId)))
            throw new ConfigurationRevisionConflictException("setKey="+meta.setKey()+" expectedBase="+text(expectedBaseRevisionId)+" currentActive="+currentActive);
        RuntimeConfigurationConfigSet set=existingSet.orElseGet(()->revisionService.createSet(meta.setKey(),environment,ConfigurationScope.COMPONENT,meta.owner(),meta.owner(),actor));
        Map<String,Object> candidate=currentCanonicalValues(meta.setKey(),currentActive);
        candidate.put(meta.key(),normalized);
        validateSet(meta.setKey(),candidate);
        RuntimeConfigurationRevision draft=revisionService.createDraft(set.configSetId(),actor,normalizedReason,correlationId);
        for(SettingMeta member:settingsForSet(meta.setKey())) revisionService.putValue(draft.revisionId(),member.key(),encode(candidate.get(member.key())),actor,normalizedReason,correlationId);
        revisionService.validate(draft.revisionId(),actor,normalizedReason,correlationId);
        RuntimeConfigurationRevision pending=revisionService.submitForApproval(draft.revisionId(),actor,normalizedReason,correlationId);
        log.info("runtime_config_change_request_pending key={} setKey={} revisionId={} baseRevisionId={} actor={} correlationId={}",
                meta.key(), meta.setKey(), pending.revisionId(), currentActive, actor, text(correlationId));
        return revisionView(pending);
    }

    @Transactional
    public RevisionView approve(String revisionId,String reason,String actor,String correlationId){
        log.info("runtime_config_revision_approve_started revisionId={} actor={} correlationId={}", revisionId, actor, text(correlationId));
        RuntimeConfigurationRevision approved = revisionService.approve(revisionId,actor,required(reason,"reason"),correlationId);
        log.info("runtime_config_revision_approved revisionId={} state={} actor={} correlationId={}", approved.revisionId(), approved.state(), actor, text(correlationId));
        return revisionView(approved);
    }

    @Transactional
    public RevisionView reject(String revisionId,String reason,String actor,String correlationId){
        log.info("runtime_config_revision_reject_started revisionId={} actor={} correlationId={}", revisionId, actor, text(correlationId));
        RuntimeConfigurationRevision rejected = revisionService.reject(revisionId,actor,required(reason,"reason"),correlationId);
        log.info("runtime_config_revision_rejected revisionId={} state={} actor={} correlationId={}", rejected.revisionId(), rejected.state(), actor, text(correlationId));
        return revisionView(rejected);
    }

    @Transactional
    public RevisionView publishApproved(String revisionId,String expectedBaseRevisionId,String reason,String actor,String correlationId){
        log.info("runtime_config_revision_publish_started revisionId={} expectedBaseRevisionId={} actor={} correlationId={}",
                revisionId, text(expectedBaseRevisionId), actor, text(correlationId));
        RuntimeConfigurationRevision published=revisionService.publish(revisionId,text(expectedBaseRevisionId),actor,required(reason,"reason"),correlationId);
        log.info("runtime_config_revision_published revisionId={} configSetId={} sequenceNo={} actor={} correlationId={}",
                published.revisionId(), published.configSetId(), published.sequenceNo(), actor, text(correlationId));
        return revisionView(published);
    }

    @Transactional
    public RevisionView requestRollback(String key,String restoreSourceRevisionId,String reason,String actor,String correlationId){
        SettingMeta meta=requireEditableSetting(key);
        log.info("runtime_config_rollback_request_started key={} setKey={} restoreSourceRevisionId={} actor={} correlationId={}",
                meta.key(), meta.setKey(), restoreSourceRevisionId, actor, text(correlationId));
        RuntimeConfigurationConfigSet set=revisions.findConfigSetBySetKey(environment,meta.setKey()).orElseThrow(()->new IllegalStateException("No governed Runtime Configuration revision exists for "+meta.label()));
        RuntimeConfigurationRevision draft=revisionService.createRollbackDraft(set.configSetId(),required(restoreSourceRevisionId,"restoreSourceRevisionId"),actor,required(reason,"reason"),correlationId);
        revisionService.validate(draft.revisionId(),actor,reason,correlationId);
        RuntimeConfigurationRevision pending = revisionService.submitForApproval(draft.revisionId(),actor,reason,correlationId);
        log.info("runtime_config_rollback_request_pending key={} setKey={} revisionId={} restoreSourceRevisionId={} actor={} correlationId={}",
                meta.key(), meta.setKey(), pending.revisionId(), restoreSourceRevisionId, actor, text(correlationId));
        return revisionView(pending);
    }

    @Transactional
    public EmergencyOverrideView createEmergencyOverride(String key,Object requestedValue,int ttlMinutes,String reason,String actor,String correlationId){
        SettingMeta meta=requireEditableSetting(key);
        if(ttlMinutes<5||ttlMinutes>240) throw new IllegalArgumentException("Emergency override TTL must be between 5 and 240 minutes");
        RuntimeConfigurationConfigSet set=revisions.findConfigSetBySetKey(environment,meta.setKey()).orElseThrow(()->new IllegalStateException("Emergency override requires an active governed Runtime Configuration revision"));
        String active=revisions.findActiveRevisionId(set.configSetId()).orElseThrow(()->new IllegalStateException("Emergency override requires an active published revision"));
        Object normalized=normalizeValue(meta,requestedValue);
        Map<String,Object> candidate=currentCanonicalValues(meta.setKey(),active);
        candidate.put(meta.key(),normalized);
        validateSet(meta.setKey(),candidate);
        String canonical=encode(normalized);
        log.info("runtime_config_override_create_started key={} setKey={} activeRevisionId={} ttlMinutes={} actor={} correlationId={}",
                meta.key(), meta.setKey(), active, ttlMinutes, actor, text(correlationId));
        RuntimeConfigurationEmergencyOverride created=governance.createEmergencyOverride(UUID.randomUUID().toString(),set.configSetId(),meta.key(),active,canonical,sha256(canonical),
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(ttlMinutes),actor,required(reason,"reason"),correlationId);
        distribution.requestRedistribution(set.configSetId(),active,"Emergency override created: "+created.overrideId());
        log.info("runtime_config_override_created overrideId={} key={} configSetId={} baseRevisionId={} expiresAt={} actor={} correlationId={}",
                created.overrideId(), meta.key(), set.configSetId(), active, created.expiresAt(), actor, text(correlationId));
        return emergencyView(created);
    }

    @Transactional
    public EmergencyOverrideView revokeEmergencyOverride(String overrideId,String reason,String actor,String correlationId){
        log.info("runtime_config_override_revoke_started overrideId={} actor={} correlationId={}", overrideId, actor, text(correlationId));
        RuntimeConfigurationEmergencyOverride revoked=governance.revokeEmergencyOverride(overrideId,actor,required(reason,"reason"),correlationId);
        String current=revisions.findActiveRevisionId(revoked.configSetId()).orElseThrow(()->new IllegalStateException("Config Set has no active revision"));
        distribution.requestRedistribution(revoked.configSetId(),current,"Emergency override revoked: "+overrideId);
        log.info("runtime_config_override_revoked overrideId={} key={} configSetId={} activeRevisionId={} actor={} correlationId={}",
                overrideId, revoked.definitionKey(), revoked.configSetId(), current, actor, text(correlationId));
        return emergencyView(revoked);
    }

    @Transactional(readOnly=true)
    public ApplyStateView applyState(String key){
        SettingMeta meta=requireSetting(key);
        Optional<RuntimeConfigurationConfigSet> set=revisions.findConfigSetBySetKey(environment,meta.setKey());
        if(set.isEmpty()) return new ApplyStateView(null,null,null,0,0,0,0,0,List.of());
        String setId=set.get().configSetId();
        String active=revisions.findActiveRevisionId(setId).orElse(null);
        String desiredFingerprint=active==null?null:snapshotProjections.desiredSnapshotFingerprint(setId);
        List<NodeApplyView> nodes=new ArrayList<>();
        int applied=0,notSeen=0,stale=0,failed=0;
        for(RuntimeConfigurationNodeApplyState state:distribution.listRequiredApplyStates(setId)){
            boolean matches=active!=null && active.equals(state.desiredRevisionId()) && active.equals(state.appliedRevisionId())
                    && desiredFingerprint!=null && desiredFingerprint.equalsIgnoreCase(text(state.snapshotFingerprint()));
            String projected=state.state().name();
            if(state.state()==RuntimeConfigurationApplyStatus.APPLIED && !matches) projected="DESIRED";
            if("APPLIED".equals(projected)) applied++;
            else if("NOT_SEEN".equals(projected)) notSeen++;
            else if("STALE".equals(projected)) stale++;
            else if("FAILED".equals(projected)) failed++;
            nodes.add(new NodeApplyView(state.nodeId(),state.nodeRole(),state.nodeInstanceId(),active,state.desiredRevisionId(),
                    state.appliedRevisionId(),desiredFingerprint,"APPLIED".equals(projected)?state.snapshotFingerprint():null,projected,matches,state.lastSeenAt(),state.appliedAt(),
                    state.errorCode(),state.errorDetail(),state.authorityRuntimeState(),state.snapshotExpiresAt(),state.authorityObservedAt()));
        }
        return new ApplyStateView(setId,active,desiredFingerprint,nodes.size(),applied,notSeen,stale,failed,List.copyOf(nodes));
    }

    @Transactional(readOnly=true)
    public RollbackPreview rollbackPreview(String key,String restoreSourceRevisionId){
        SettingMeta meta=requireSetting(key);
        RuntimeConfigurationConfigSet set=revisions.findConfigSetBySetKey(environment,meta.setKey())
                .orElseThrow(()->new IllegalStateException("No governed Runtime Configuration revision exists for "+meta.label()));
        String sourceId=required(restoreSourceRevisionId,"restoreSourceRevisionId");
        RuntimeConfigurationRevision source=revisions.findRevision(sourceId).orElseThrow(()->new IllegalArgumentException("Restore revision not found: "+sourceId));
        if(!source.configSetId().equals(set.configSetId())) throw new IllegalArgumentException("Restore revision belongs to another Config Set");
        if(source.state()!=RuntimeConfigurationRevisionState.PUBLISHED && source.state()!=RuntimeConfigurationRevisionState.SUPERSEDED)
            throw new IllegalArgumentException("Rollback source must be a previously published revision");
        String currentId=revisions.findActiveRevisionId(set.configSetId()).orElse(null);
        Map<String,Object> current=currentId==null?Map.of():revisionValues(currentId);
        Map<String,Object> restore=revisionValues(sourceId);
        List<RollbackDiff> diffs=new ArrayList<>();
        java.util.Set<String> keys=new java.util.TreeSet<>();keys.addAll(current.keySet());keys.addAll(restore.keySet());
        for(String definitionKey:keys){
            Object before=current.get(definitionKey);Object after=restore.get(definitionKey);
            if(!Objects.equals(before,after)) diffs.add(new RollbackDiff(definitionKey,settings.containsKey(definitionKey)?settings.get(definitionKey).label():definitionKey,before,after));
        }
        return new RollbackPreview(set.configSetId(),currentId,sourceId,source.sequenceNo(),List.copyOf(diffs));
    }

    @Transactional(readOnly=true)
    public List<RevisionView> revisions(String key){
        SettingMeta meta=requireSetting(key);
        return revisions.findConfigSetBySetKey(environment,meta.setKey()).map(set->revisions.listRevisions(set.configSetId(),50).stream().map(this::revisionView).toList()).orElse(List.of());
    }

    @Transactional(readOnly=true)
    public GovernanceOverview governance(){
        Map<String,RevisionView> pendingByRevision=new LinkedHashMap<>();
        Map<String,EmergencyOverrideView> overridesById=new LinkedHashMap<>();
        for(String setKey:uniqueSetKeys()){
            revisions.findConfigSetBySetKey(environment,setKey).ifPresent(set->{
                revisions.listRevisions(set.configSetId(),50).stream()
                        .filter(r->r.state()==RuntimeConfigurationRevisionState.PENDING_APPROVAL||r.state()==RuntimeConfigurationRevisionState.APPROVED)
                        .map(this::revisionView).forEach(view->pendingByRevision.putIfAbsent(view.revisionId(),view));
                governance.listActiveEmergencyOverrides(set.configSetId()).stream().map(this::emergencyView)
                        .forEach(view->overridesById.putIfAbsent(view.overrideId(),view));
            });
        }
        return new GovernanceOverview(List.copyOf(pendingByRevision.values()),List.copyOf(overridesById.values()),
                "Approval and emergency-override projections are deduplicated by Config Set identity; Category navigation never duplicates governance work.");
    }


    private Set<String> uniqueSetKeys(){
        Set<String> keys=new LinkedHashSet<>();
        for(CategoryMeta category:categories) if(category.migrationAuthorizedCount()>0) keys.addAll(category.setKeys());
        return Collections.unmodifiableSet(keys);
    }

    private String setLabel(String setKey){
        List<String> titles=categories.stream().filter(category->category.setKeys().contains(setKey)).map(CategoryMeta::title).toList();
        return titles.isEmpty()?setKey:String.join(" / ",titles);
    }

    private SetStatus aggregateSetStatus(Set<String> setKeys,Map<String,SetStatus> statuses){
        if(setKeys==null||setKeys.isEmpty()) return new SetStatus("NOT_MIGRATED",0,0);
        int applied=0,known=0;String state="APPLIED";
        for(String setKey:setKeys){
            SetStatus current=statuses.getOrDefault(setKey,new SetStatus("STARTUP_FALLBACK",0,0));
            applied+=current.appliedNodes();known+=current.knownNodes();
            if("FAILED".equals(current.applicationStatus())||"CONFIGURATION_INCOMPLETE".equals(current.applicationStatus())) state="FAILED";
            else if(!"FAILED".equals(state) && "PARTIALLY_APPLIED".equals(current.applicationStatus())) state="PARTIALLY_APPLIED";
            else if(!"FAILED".equals(state) && !"PARTIALLY_APPLIED".equals(state) && "PUBLISHED_APPLYING".equals(current.applicationStatus())) state="PUBLISHED_APPLYING";
            else if("APPLIED".equals(state) && "STARTUP_FALLBACK".equals(current.applicationStatus())) state="STARTUP_FALLBACK";
        }
        return new SetStatus(state,applied,known);
    }

    private String categoryLifecycle(CategoryMeta category){
        if(category.definitionCount()==0) return "PLANNED";
        long migrated=category.definitionKeys().stream().filter(authorityRegistry::isRuntimeAuthoritative).count();
        if(migrated==category.definitionCount()) return "MIGRATED";
        if(migrated>0) return "MIXED";
        if(category.migrationAuthorizedCount()>0) return "MIGRATING";
        return "PLANNED";
    }

    private RevisionView revisionView(RuntimeConfigurationRevision r){
        return new RevisionView(r.revisionId(),r.configSetId(),r.sequenceNo(),r.baseRevisionId(),r.rollbackOfRevisionId(),r.restoreSourceRevisionId(),r.state().name(),r.reason(),r.createdBy(),r.createdAt(),r.submittedBy(),r.submittedAt(),r.approvedBy(),r.approvedAt(),r.publishedBy(),r.publishedAt());
    }
    private EmergencyOverrideView emergencyView(RuntimeConfigurationEmergencyOverride o){
        return new EmergencyOverrideView(o.overrideId(),o.configSetId(),o.definitionKey(),o.baseRevisionId(),decodeJson(o.valueJson()),o.status(),o.reason(),o.createdBy(),o.createdAt(),o.expiresAt(),o.revokedBy(),o.revokedAt(),o.revokeReason());
    }

    private SettingSummary summary(String key) {
        SettingDetail detail = detail(key);
        return new SettingSummary(detail.key(), detail.label(), detail.description(), detail.localEffectiveValue(), detail.desiredValue(), detail.unit(), detail.risk(), detail.effect(), detail.applicationStatus());
    }

    private SetStatus setStatus(String setKey) {
        if (setKey == null) return new SetStatus("NOT_MIGRATED", 0, 0);
        Optional<RuntimeConfigurationConfigSet> set = revisions.findConfigSetBySetKey(environment, setKey);
        if (set.isEmpty()) return new SetStatus("STARTUP_FALLBACK", 0, 0);
        Optional<String> active = revisions.findActiveRevisionId(set.get().configSetId());
        if (active.isEmpty()) return new SetStatus("STARTUP_FALLBACK", 0, 0);
        String desiredFingerprint=snapshotProjections.desiredSnapshotFingerprint(set.get().configSetId());
        List<RuntimeConfigurationNodeApplyState> states = distribution.listRequiredApplyStates(set.get().configSetId());
        if (states.isEmpty()) return new SetStatus("PUBLISHED_APPLYING", 0, 0);
        int applied = 0;
        boolean failed = false;
        boolean pending = false;
        for (RuntimeConfigurationNodeApplyState state : states) {
            boolean matches = active.get().equals(state.desiredRevisionId()) && active.get().equals(state.appliedRevisionId())
                    && desiredFingerprint.equalsIgnoreCase(text(state.snapshotFingerprint()));
            if (state.state() == RuntimeConfigurationApplyStatus.APPLIED && matches) applied++;
            else if (state.state() == RuntimeConfigurationApplyStatus.FAILED || state.state() == RuntimeConfigurationApplyStatus.STALE) failed = true;
            else pending = true;
        }
        if (applied == states.size()) return new SetStatus("APPLIED", applied, states.size());
        if (failed && applied == 0 && !pending) return new SetStatus("FAILED", applied, states.size());
        if (failed || applied > 0) return new SetStatus("PARTIALLY_APPLIED", applied, states.size());
        return new SetStatus(pending ? "PUBLISHED_APPLYING" : "PARTIALLY_APPLIED", applied, states.size());
    }

    private Map<String, Object> currentCanonicalValues(String setKey, String activeRevisionId) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (activeRevisionId != null) {
            for (RuntimeConfigurationRevisionItem item : revisions.listItems(activeRevisionId)) values.put(item.definitionKey(), decode(item));
        } else {
            for (SettingMeta meta : settingsForSet(setKey)) values.put(meta.key(), runtimeValue(meta));
        }
        for (SettingMeta meta : settingsForSet(setKey)) {
            // Rolling-upgrade seed: keys introduced after the active revision are copied from the
            // currently effective/startup value into the next draft. V244 completeness guards
            // require every subsequently validated revision to contain the new key.
            values.putIfAbsent(meta.key(), runtimeValue(meta));
        }
        return values;
    }

    private Object runtimeValue(SettingMeta meta) {
        if (effectiveValueResolvers.supports(meta.key())) {
            return effectiveValueResolvers.resolve(meta.key());
        }
        return localSnapshotValues.canonicalJsonValue(meta.setKey(), meta.key())
                .map(this::decodeJson)
                .orElseThrow(() -> new IllegalStateException(
                        "RUNTIME_CONFIG_EFFECTIVE_VALUE_UNAVAILABLE key=" + meta.key()
                        + " setKey=" + meta.setKey()
                        + " reason=NO_LOCAL_TYPED_RESOLVER_OR_AUTHENTICATED_SNAPSHOT"));
    }

    private Object normalizeValue(SettingMeta meta, Object value) {
        if (value == null) throw new IllegalArgumentException(meta.label() + " is required");
        return switch (meta.dataType()) {
            case "BOOLEAN" -> booleanValue(value,meta.key());
            case "INTEGER" -> integer(value, meta.key());
            case "LONG" -> longValue(value, meta.key());
            case "DECIMAL" -> decimalValue(value,meta.key());
            case "DURATION" -> duration(value, meta.key()).toString();
            case "STRING" -> stringValue(value,meta.validation(),meta.key());
            case "ENUM", "URI" -> required(String.valueOf(value),meta.key());
            case "JSON" -> value;
            default -> throw new IllegalArgumentException("Unsupported governed Runtime Configuration data type: " + meta.dataType());
        };
    }

    private void validateSet(String setKey, Map<String, Object> values) {
        List<SettingMeta> members=settingsForSet(setKey);
        if(members.isEmpty()) throw new IllegalArgumentException("Unsupported Runtime Configuration Set: "+setKey);
        for(SettingMeta meta:members){
            if(!values.containsKey(meta.key())) throw new IllegalArgumentException("Runtime Configuration value is required: "+meta.key());
            Object value=values.get(meta.key());
            Map<String,Object> rule=meta.validation();
            switch(meta.dataType()){
                case "BOOLEAN" -> booleanValue(value,meta.key());
                case "INTEGER" -> validateNumericBounds(integer(value,meta.key()),rule,meta.key());
                case "LONG" -> validateNumericBounds(longValue(value,meta.key()),rule,meta.key());
                case "DECIMAL" -> validateDecimalBounds(decimalValue(value,meta.key()),rule,meta.key());
                case "DURATION" -> validateDurationBounds(duration(value,meta.key()),rule,meta.key());
                case "STRING" -> validateString(value,rule,meta.key());
                case "ENUM" -> validateEnum(value,rule,meta.key());
                case "URI" -> validateUri(value,rule,meta.key());
                case "JSON" -> validateJson(value,rule,meta.key());
                default -> throw new IllegalArgumentException("Unsupported governed Runtime Configuration data type: "+meta.dataType());
            }
            Object requiredWhen=rule.get("requiredWhenKeyEquals");
            if(requiredWhen instanceof Map<?,?> condition){
                Object otherKeyValue=condition.get("key"), expected=condition.get("value");
                if(otherKeyValue==null||expected==null) throw new IllegalArgumentException(meta.key()+" requiredWhenKeyEquals requires key and value");
                String otherKey=String.valueOf(otherKeyValue);
                Object other=values.get(otherKey);
                if(other!=null&&String.valueOf(expected).equalsIgnoreCase(String.valueOf(other).trim())){
                    String current=value==null?"":String.valueOf(value).trim();
                    if(current.isBlank()) throw new IllegalArgumentException(meta.label()+" is required when "+requireSetting(otherKey).label()+" is "+expected+".");
                }
            }
            Object relation=rule.get("greaterThanOrEqualKey");
            if(relation!=null){
                String otherKey=String.valueOf(relation);
                Object other=values.get(otherKey);
                if(other==null) throw new IllegalArgumentException(meta.key()+" validation requires "+otherKey);
                if("DURATION".equals(meta.dataType())){
                    if(duration(value,meta.key()).compareTo(duration(other,otherKey))<0)
                        throw new IllegalArgumentException(meta.label()+" must be greater than or equal to "+requireSetting(otherKey).label()+".");
                }else if(longValue(value,meta.key())<longValue(other,otherKey)){
                    throw new IllegalArgumentException(meta.label()+" must be greater than or equal to "+requireSetting(otherKey).label()+".");
                }
            }
        }
    }

    private static void validateNumericBounds(long value,Map<String,Object> rule,String key){
        Long min=number(rule.get("minimum"));Long max=number(rule.get("maximum"));
        if(min!=null&&value<min) throw new IllegalArgumentException(key+" must be >= "+min);
        if(max!=null&&value>max) throw new IllegalArgumentException(key+" must be <= "+max);
    }
    private static void validateDurationBounds(Duration value,Map<String,Object> rule,String key){
        Duration min=durationRule(rule.get("minimum"));Duration max=durationRule(rule.get("maximum"));
        if(min!=null&&value.compareTo(min)<0) throw new IllegalArgumentException(key+" must be >= "+min);
        if(max!=null&&value.compareTo(max)>0) throw new IllegalArgumentException(key+" must be <= "+max);
    }
    private static Long number(Object value){
        if(value==null)return null;
        if(value instanceof Number n)return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }
    private static Duration durationRule(Object value){return value==null?null:Duration.parse(String.valueOf(value));}

    private static java.math.BigDecimal decimalValue(Object value,String key){
        try{return value instanceof java.math.BigDecimal d?d:new java.math.BigDecimal(String.valueOf(value).trim());}
        catch(RuntimeException ex){throw new IllegalArgumentException("Expected decimal for "+key);}
    }
    private static boolean booleanValue(Object value,String key){
        if(value instanceof Boolean b)return b;String v=String.valueOf(value).trim();
        if("true".equalsIgnoreCase(v))return true;if("false".equalsIgnoreCase(v))return false;
        throw new IllegalArgumentException("Expected boolean for "+key);
    }
    private static void validateDecimalBounds(java.math.BigDecimal value,Map<String,Object> rule,String key){
        Object min=rule.get("minimum"),max=rule.get("maximum");
        if(min!=null&&value.compareTo(new java.math.BigDecimal(String.valueOf(min)))<0)throw new IllegalArgumentException(key+" must be >= "+min);
        if(max!=null&&value.compareTo(new java.math.BigDecimal(String.valueOf(max)))>0)throw new IllegalArgumentException(key+" must be <= "+max);
    }
    private static String stringValue(Object value,Map<String,Object> rule,String key){
        if(value==null) throw new IllegalArgumentException(key+" is required");
        String v=String.valueOf(value).trim();
        if(v.isBlank()&&Boolean.TRUE.equals(rule.get("allowBlank"))) return "";
        return required(v,key);
    }
    private static void validateString(Object value,Map<String,Object> rule,String key){
        String v=stringValue(value,rule,key);Long min=number(rule.get("minLength")),max=number(rule.get("maxLength"));
        if(min!=null&&v.length()<min)throw new IllegalArgumentException(key+" length must be >= "+min);
        if(max!=null&&v.length()>max)throw new IllegalArgumentException(key+" length must be <= "+max);
        Object pattern=rule.get("pattern");
        if(pattern!=null&&!java.util.regex.Pattern.matches(String.valueOf(pattern),v))throw new IllegalArgumentException(key+" does not match required pattern");
        validateAllowedScalar(v,rule,key);
        if(rule.get("allowedSchemes") instanceof List<?>) validateUri(v,rule,key);
        Object csvAllowed=rule.get("allowedCsvValues");
        if(csvAllowed instanceof List<?> allowed){
            java.util.Set<String> allowedValues=allowed.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
            java.util.List<String> items=java.util.Arrays.stream(v.split(",")).map(String::trim).filter(x->!x.isBlank()).toList();
            Long minItems=number(rule.get("minItems"));
            if(minItems!=null&&items.size()<minItems)throw new IllegalArgumentException(key+" must contain at least "+minItems+" item(s)");
            if(items.stream().anyMatch(x->!allowedValues.contains(x)))throw new IllegalArgumentException(key+" contains unsupported value; allowed="+allowed);
        }
    }
    private static void validateJson(Object value,Map<String,Object> rule,String key){
        if(value==null) throw new IllegalArgumentException(key+" is required");
        String jsonType=rule.get("jsonType")==null?null:String.valueOf(rule.get("jsonType")).trim().toUpperCase(java.util.Locale.ROOT);
        if("ARRAY".equals(jsonType)){
            if(!(value instanceof java.util.List<?> list)) throw new IllegalArgumentException(key+" must be a JSON array");
            Long minItems=number(rule.get("minItems")),maxItems=number(rule.get("maxItems"));
            if(minItems!=null&&list.size()<minItems) throw new IllegalArgumentException(key+" must contain at least "+minItems+" item(s)");
            if(maxItems!=null&&list.size()>maxItems) throw new IllegalArgumentException(key+" must contain at most "+maxItems+" item(s)");
            String itemType=rule.get("itemType")==null?null:String.valueOf(rule.get("itemType")).trim().toUpperCase(java.util.Locale.ROOT);
            Object pattern=rule.get("itemPattern");
            Object requiredFields=rule.get("itemRequiredFields");
            for(Object item:list){
                if("STRING".equals(itemType)){
                    if(!(item instanceof String text)) throw new IllegalArgumentException(key+" array items must be strings");
                    if(pattern!=null&&!java.util.regex.Pattern.matches(String.valueOf(pattern),text)) throw new IllegalArgumentException(key+" contains a string that does not match the required pattern");
                }else if("OBJECT".equals(itemType)){
                    if(!(item instanceof Map<?,?> map)) throw new IllegalArgumentException(key+" array items must be objects");
                    if(requiredFields instanceof java.util.List<?> fields){
                        for(Object field:fields){Object fieldValue=map.get(String.valueOf(field));if(fieldValue==null||String.valueOf(fieldValue).trim().isBlank())throw new IllegalArgumentException(key+" item requires field "+field);}
                    }
                }
            }
        }else if("OBJECT".equals(jsonType)&&!(value instanceof Map<?,?>)){
            throw new IllegalArgumentException(key+" must be a JSON object");
        }
    }

    private static void validateEnum(Object value,Map<String,Object> rule,String key){
        String v=required(String.valueOf(value),key);
        Object allowed=rule.get("allowedValues");
        if(!(allowed instanceof List<?> list)||list.isEmpty())throw new IllegalArgumentException(key+" enum definition is missing allowedValues");
        if(!list.stream().map(String::valueOf).anyMatch(v::equals))throw new IllegalArgumentException(key+" must be one of "+list);
    }
    private static void validateAllowedScalar(String value,Map<String,Object> rule,String key){
        Object allowed=rule.get("allowedValues");
        if(allowed==null)allowed=rule.get("enum");
        if(allowed instanceof List<?> list&&!list.stream().map(String::valueOf).anyMatch(value::equals))throw new IllegalArgumentException(key+" must be one of "+list);
    }
    private static void validateUri(Object value,Map<String,Object> rule,String key){
        String raw=required(String.valueOf(value),key);
        Long min=number(rule.get("minLength")),max=number(rule.get("maxLength"));
        if(min!=null&&raw.length()<min)throw new IllegalArgumentException(key+" length must be >= "+min);
        if(max!=null&&raw.length()>max)throw new IllegalArgumentException(key+" length must be <= "+max);
        try{
            java.net.URI uri=java.net.URI.create(raw);
            if(uri.getScheme()==null)throw new IllegalArgumentException();
            Object allowed=rule.get("allowedSchemes");
            if(allowed instanceof List<?> list&&!list.stream().map(String::valueOf).anyMatch(s->s.equalsIgnoreCase(uri.getScheme())))
                throw new IllegalArgumentException(key+" URI scheme must be one of "+list);
        }catch(IllegalArgumentException ex){
            if(ex.getMessage()!=null&&ex.getMessage().startsWith(key+" URI scheme"))throw ex;
            throw new IllegalArgumentException("Expected absolute URI for "+key);
        }
    }
    private Map<String,Object> revisionValues(String revisionId){
        Map<String,Object> values=new LinkedHashMap<>();for(RuntimeConfigurationRevisionItem item:revisions.listItems(revisionId))values.put(item.definitionKey(),decode(item));return values;
    }

    private Object decode(RuntimeConfigurationRevisionItem item) {
        try { return json.readValue(item.valueJson(), Object.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid canonical configuration JSON for " + item.definitionKey(), ex); }
    }
    private Object decodeJson(String value){try{return json.readValue(value,Object.class);}catch(Exception e){throw new IllegalStateException("Stored Runtime Configuration JSON is invalid",e);}}
    private static String sha256(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Unable to encode configuration value", ex); }
    }

    private SettingMeta requireSetting(String key) {
        SettingMeta meta = settings.get(key);
        if (meta == null) throw new IllegalArgumentException("Runtime Configuration target not found: " + key);
        return meta;
    }
    private SettingMeta requireEditableSetting(String key){
        SettingMeta meta=requireSetting(key);
        if(!meta.migrationAuthorized()) throw new IllegalStateException("Runtime Configuration setting is not migration-authorized: "+key);
        if(!meta.editable()) throw new IllegalStateException("Runtime Configuration setting is read-only: "+key);
        return meta;
    }
    private List<SettingMeta> settingsForSet(String setKey) { return settings.values().stream().filter(v -> v.migrationAuthorized() && v.setKey().equals(setKey)).toList(); }
    private static int i(Map<String,Object> values,String key){ return integer(values.get(key), key); }
    private static long l(Map<String,Object> values,String key){ return longValue(values.get(key), key); }
    private static int integer(Object value,String key){
        try{
            if(value instanceof Number n) return new java.math.BigDecimal(n.toString()).intValueExact();
            return Integer.parseInt(String.valueOf(value).trim());
        }catch(RuntimeException ex){throw new IllegalArgumentException("Expected integer for "+key);}
    }
    private static long longValue(Object value,String key){
        try{
            if(value instanceof Number n) return new java.math.BigDecimal(n.toString()).longValueExact();
            return Long.parseLong(String.valueOf(value).trim());
        }catch(RuntimeException ex){throw new IllegalArgumentException("Expected integer for "+key);}
    }
    private static Duration duration(Object value,String key){
        try{Duration parsed=Duration.parse(String.valueOf(value).trim());positive(parsed,key+" must be positive.");return parsed;}
        catch(RuntimeException ex){if(ex instanceof IllegalArgumentException iae && iae.getMessage()!=null && iae.getMessage().contains("must be positive"))throw iae;throw new IllegalArgumentException("Expected ISO-8601 duration for "+key+" (for example PT5S or PT1M)");}
    }
    private static void positive(Duration value,String message){if(value==null||value.isZero()||value.isNegative())throw new IllegalArgumentException(message);}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static String text(String value){return value==null||value.isBlank()?null:value.trim();}
    private static boolean same(String a,String b){return a==null?b==null:a.equals(b);}
    private static String environmentLabel(OpenDispatchEnvironment value){return switch(value){case PRD->"PRODUCTION";case UAT->"UAT";case SIT->"SIT";case QA->"QA";case DEV->"DEVELOPMENT";case LOCAL->"LOCAL";};}

    private List<CategoryMeta> loadCategories(RuntimeConfigurationDefinitionStore store) {
        List<RuntimeConfigurationDefinition> all = runtimeTargetDefinitions(store);
        Map<String, MutableCategoryMeta> grouped = new LinkedHashMap<>();
        for (RuntimeConfigurationDefinition definition : all) {
            Map<String,Object> ui = objectMap(definition.uiMetadataJson(), definition.key()+" uiMetadata");
            String categoryId = string(ui,"categoryId");
            MutableCategoryMeta category = grouped.computeIfAbsent(categoryId, id -> new MutableCategoryMeta(
                    id,
                    optionalString(ui,"categoryDisplayName",humanizeCategoryId(id)),
                    optionalString(ui,"categoryDescription","Runtime configuration definitions for "+humanizeCategoryId(id)+"."),
                    integerOrDefault(ui.get("categoryOrder"),1000),
                    definition.owner()));
            category.definitionKeys.add(definition.key());
            if(definition.configSetKey()!=null && !definition.configSetKey().isBlank()) category.setKeys.add(definition.configSetKey());
            if (definition.migrationAuthorized()) category.migrationAuthorizedCount++;
            if (definition.adminEditable() && definition.migrationAuthorized()) category.editableCount++;
        }
        return grouped.values().stream()
                .sorted(Comparator.comparingInt((MutableCategoryMeta c)->c.order).thenComparing(c->c.title))
                .map(c->new CategoryMeta(c.id,c.title,c.description,Collections.unmodifiableSet(new LinkedHashSet<>(c.setKeys)),c.owner,c.order,
                        Collections.unmodifiableSet(new LinkedHashSet<>(c.definitionKeys)),c.migrationAuthorizedCount,c.editableCount))
                .toList();
    }

    private MigrationCoverage migrationCoverage() {
        List<RuntimeConfigurationDefinition> all = runtimeTargetDefinitions(definitions);
        int total = all.size();
        int authorized = 0;
        int editable = 0;
        int authoritative = 0;
        int proposed = 0;
        for (RuntimeConfigurationDefinition definition : all) {
            if (definition.migrationAuthorized()) authorized++;
            if (definition.adminEditable() && definition.migrationAuthorized()) editable++;
            if (authorityRegistry.isRuntimeAuthoritative(definition.key())) authoritative++;
            if (!definition.migrationAuthorized()) proposed++;
        }
        return new MigrationCoverage(total, authorized, authoritative, editable, proposed,
                Math.max(0,total-authorized), Math.max(0,authorized-authoritative));
    }

    private List<RuntimeConfigurationDefinition> runtimeTargetDefinitions(RuntimeConfigurationDefinitionStore store){
        List<RuntimeConfigurationDefinition> all=store.listAll();
        if(all==null||all.isEmpty()) all=store.listMigrationAuthorized();
        return all.stream().filter(definition->isRuntimeTargetAuthorityClass(definition.authorityClass()))
                .filter(definition->!"RETIRED".equalsIgnoreCase(definition.reviewStatus())).toList();
    }

    private static boolean isRuntimeTargetAuthorityClass(String authorityClass){
        if(authorityClass==null) return false;
        String value=authorityClass.trim().toUpperCase();
        return "RUNTIME_TUNABLE".equals(value) || "RUNTIME_CONFIG".equals(value) || "RUNTIME_CONFIG_DB".equals(value);
    }

    private static String normalizeMigrationState(String reviewStatus){
        if(reviewStatus==null||reviewStatus.isBlank()) return "PROPOSED";
        String value=reviewStatus.trim().toUpperCase();
        return switch(value){case "MIGRATED","LEGACY_RETIRED","MIGRATION_READY"->value;default->"PROPOSED";};
    }

    private static String optionalString(Map<String,Object> values,String key,String fallback){
        Object value=values.get(key);return value==null||String.valueOf(value).isBlank()?fallback:String.valueOf(value);
    }
    private static int integerOrDefault(Object value,int fallback){
        if(value==null)return fallback;try{return Integer.parseInt(String.valueOf(value));}catch(RuntimeException ex){return fallback;}
    }
    private static String humanizeCategoryId(String id){
        String[] parts=id.split("[-_.]+");StringBuilder out=new StringBuilder();
        for(String part:parts){if(part.isBlank())continue;if(out.length()>0)out.append(' ');String lower=part.toLowerCase();
            if(lower.equals("mcp")||lower.equals("a2a")||lower.equals("api")||lower.equals("ui"))out.append(lower.toUpperCase());
            else out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));}
        return out.length()==0?id:out.toString();
    }

    private static final class MutableCategoryMeta {
        private final String id; private final String title; private final String description; private final int order; private final String owner;
        private final Set<String> setKeys=new LinkedHashSet<>(); private final Set<String> definitionKeys=new LinkedHashSet<>(); private int migrationAuthorizedCount; private int editableCount;
        private MutableCategoryMeta(String id,String title,String description,int order,String owner){this.id=id;this.title=title;this.description=description;this.order=order;this.owner=owner;}
    }

    private Map<String, SettingMeta> loadSettings(RuntimeConfigurationDefinitionStore store) {
        Map<String, SettingMeta> values = new LinkedHashMap<>();
        for (RuntimeConfigurationDefinition definition : runtimeTargetDefinitions(store)) {
            Map<String,Object> ui = objectMap(definition.uiMetadataJson(), definition.key()+" uiMetadata");
            Map<String,Object> validation = objectMap(definition.validationJson(), definition.key()+" validation");
            SettingMeta meta = new SettingMeta(
                string(ui,"categoryId"), definition.configSetKey(), definition.owner(), definition.key(),
                definition.displayName(), optionalString(ui,"description",definition.displayName()), optionalString(ui,"recommended","Review the current effective value and validation constraints before changing this setting."), definition.unit(),
                definition.dataType(), definition.risk(), optionalString(ui,"effect",definition.mutability()), optionalString(ui,"impactPositive","Runtime behavior can be adjusted without changing application code."),
                optionalString(ui,"impactTradeoff","Changing this value may affect runtime load, latency or recovery behavior."), definition.scope(), definition.mutability(), definition.adminEditable(),
                definition.requiresApproval(), definition.migrationAuthorized(), definition.reviewStatus(), definition.authorityClass(), validation);
            SettingMeta prior=values.put(meta.key(),meta);
            if(prior!=null) throw new IllegalStateException("Duplicate Runtime Configuration definition materialization: "+meta.key());
        }
        if(values.isEmpty()) throw new IllegalStateException("No Runtime Configuration target definitions were materialized");
        return Collections.unmodifiableMap(values);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> objectMap(String raw,String label){
        try{
            Object value=json.readValue(raw,Map.class);
            if(!(value instanceof Map<?,?> map)) throw new IllegalStateException(label+" must be a JSON object");
            return (Map<String,Object>)map;
        }catch(Exception ex){throw new IllegalStateException("Invalid "+label,ex);}
    }
    private static String string(Map<String,Object> values,String key){
        Object value=values.get(key);if(value==null||String.valueOf(value).isBlank())throw new IllegalStateException("Definition metadata field is required: "+key);return String.valueOf(value);
    }

    private record SettingMeta(String categoryId,String setKey,String owner,String key,String label,String description,String recommended,String unit,String dataType,String risk,String effect,String impactPositive,String impactTradeoff,String scope,String mutability,boolean editable,boolean requiresApproval,boolean migrationAuthorized,String reviewStatus,String authorityClass,Map<String,Object> validation) {}
    private record CategoryMeta(String id,String title,String description,Set<String> setKeys,String owner,int order,Set<String> definitionKeys,int migrationAuthorizedCount,int editableCount) { int definitionCount(){return definitionKeys.size();} }
    private record SetStatus(String applicationStatus,int appliedNodes,int knownNodes) {}

    public record MigrationCoverage(int runtimeTargetDefinitions,int migrationAuthorized,int runtimeAuthoritative,int adminEditable,int proposed,int pendingConsumerMigration,int pendingCutover) {}
    public record Overview(String environment,String environmentLabel,String health,int activeChanges,int appliedNodes,int knownNodes,int pendingApproval,MigrationCoverage migrationCoverage,List<Category> categories,List<RecentChange> recentChanges,String governanceNotice) {}
    public record Category(String id,String title,String description,String lifecycle,String applicationStatus,List<SettingSummary> settings,int definitionCount,int migrationAuthorizedCount,int editableCount,List<String> configSetKeys,boolean readOnly) {}
    public record SettingSummary(String key,String label,String description,Object runtimeValue,Object desiredValue,String unit,String risk,String effect,String applicationStatus) {}
    public record SettingDetail(String categoryId,String key,String label,String description,String recommended,String unit,String dataType,String risk,String effect,String impactPositive,String impactTradeoff,Object runtimeValue,Object localEffectiveValue,Object desiredValue,boolean valuePresentInActiveRevision,String authoritySource,String effectiveSource,String configurationState,String convergenceState,String migrationState,String authorityMode,String applicationStatus,String authority,String owner,String scope,String mutability,String configSetKey,String configSetId,String activeRevisionId,String desiredRevisionId,String localAppliedRevisionId,int appliedNodes,int knownNodes,boolean editable,Map<String,Object> validation,String errorCode,String errorDetail,String governanceNotice) {}
    public record RecentChange(String category,String configSetKey,String revisionId,long sequenceNo,String state,String reason,String actor,OffsetDateTime publishedAt) {}
    public record GovernanceOverview(List<RevisionView> pendingRevisions,List<EmergencyOverrideView> activeEmergencyOverrides,String notice) {}
    public record RevisionView(String revisionId,String configSetId,long sequenceNo,String baseRevisionId,String rollbackOfRevisionId,String restoreSourceRevisionId,String state,String reason,String createdBy,OffsetDateTime createdAt,String submittedBy,OffsetDateTime submittedAt,String approvedBy,OffsetDateTime approvedAt,String publishedBy,OffsetDateTime publishedAt) {}
    public record EmergencyOverrideView(String overrideId,String configSetId,String definitionKey,String baseRevisionId,Object value,String status,String reason,String createdBy,OffsetDateTime createdAt,OffsetDateTime expiresAt,String revokedBy,OffsetDateTime revokedAt,String revokeReason) {}
    public record ApplyStateView(String configSetId,String activeRevisionId,String desiredFingerprint,int requiredNodes,int appliedNodes,int notSeenNodes,int staleNodes,int failedNodes,List<NodeApplyView> nodes) {}
    public record NodeApplyView(String nodeId,String nodeRole,String nodeInstanceId,String activeRevisionId,String desiredRevisionId,String appliedRevisionId,String desiredFingerprint,String appliedFingerprint,String state,boolean matchesDesired,OffsetDateTime lastSeenAt,OffsetDateTime appliedAt,String errorCode,String errorDetail,String authorityRuntimeState,OffsetDateTime snapshotExpiresAt,OffsetDateTime authorityObservedAt) {}
    public record RollbackPreview(String configSetId,String currentRevisionId,String restoreSourceRevisionId,long restoreSourceSequence,List<RollbackDiff> changes) {}
    public record RollbackDiff(String key,String label,Object currentValue,Object restoreValue) {}
}
