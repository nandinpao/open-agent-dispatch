package com.opensocket.aievent.core.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationAuthorityClass;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationConsumerContract;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationInventoryGovernanceStatus;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationMutability;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationRisk;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationScope;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinition;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinitionStore;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernance;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernanceEvent;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryObservation;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Human-governed migration factory for executable-source configuration observations. */
@Service
public class ConfigurationMigrationGovernanceService {
    private static final int DEFAULT_LIMIT = 250;
    private final ConfigurationInventoryGovernanceStore store;
    private final RuntimeConfigurationDefinitionStore definitions;
    private final ObjectMapper objectMapper;

    public ConfigurationMigrationGovernanceService(ConfigurationInventoryGovernanceStore store, RuntimeConfigurationDefinitionStore definitions, ObjectMapper objectMapper) {
        this.store = store;
        this.definitions = definitions;
        this.objectMapper = objectMapper;
    }

    public List<InventorySummary> list(String status, String namespace, String query, Integer limit) {
        ConfigurationInventoryGovernanceStatus parsed = optionalStatus(status);
        return store.listObservations(parsed, namespace, query, limit == null ? DEFAULT_LIMIT : limit).stream().map(this::summary).toList();
    }

    public InventoryDetail detail(String key) {
        ConfigurationInventoryObservation observation = observation(key);
        ConfigurationInventoryGovernance governance = governance(key);
        RuntimeConfigurationDefinition definition = definitions.findByKey(key).orElse(null);
        return new InventoryDetail(summary(observation), governanceView(governance), readiness(observation, governance, definition),
            definitionView(definition), store.listEvents(key, 100).stream().map(this::eventView).toList());
    }

    public InventoryDetail classify(String key, ClassificationRequest request, String actor) {
        requireReason(request.reason());
        ConfigurationInventoryObservation observation = observation(key);
        ConfigurationInventoryGovernance current = governance(key);
        assertSource(observation, request.expectedSourceObservationHash());
        assertVersion(current, request.expectedVersion());
        requireState(current, ConfigurationInventoryGovernanceStatus.DISCOVERED, ConfigurationInventoryGovernanceStatus.CLASSIFIED);

        String authorityClass = enumName(ConfigurationAuthorityClass.class, request.authorityClass(), "authorityClass");
        String scope = enumName(ConfigurationScope.class, request.scope(), "scope");
        String risk = enumName(ConfigurationRisk.class, request.risk(), "risk");
        String mutability = enumName(ConfigurationMutability.class, request.mutability(), "mutability");
        String consumer = enumName(ConfigurationConsumerContract.class, request.consumerContract(), "consumerContract");
        String owner = required(request.domainOwner(), "domainOwner");
        OffsetDateTime now = now();
        ConfigurationInventoryGovernance next = new ConfigurationInventoryGovernance(key, ConfigurationInventoryGovernanceStatus.CLASSIFIED,
            observation.sourceObservationHash(), owner, authorityClass, scope, risk, mutability, consumer,
            Objects.requireNonNullElse(request.adminEditable(), false), Objects.requireNonNullElse(request.requiresApproval(), true),
            actor, now, null, null, null, null, null, null, request.reason().trim(), current.version(), current.updatedAt());
        store.save(next, current.version(), "CLASSIFIED", actor, request.reason(), "{}");
        return detail(key);
    }

    public InventoryDetail ownerReview(String key, ReviewRequest request, String actor) {
        requireReason(request.reason());
        ConfigurationInventoryObservation observation = observation(key); ConfigurationInventoryGovernance current = governance(key);
        assertSource(observation, request.expectedSourceObservationHash()); assertVersion(current, request.expectedVersion());
        requireState(current, ConfigurationInventoryGovernanceStatus.CLASSIFIED);
        ConfigurationInventoryGovernance next = copy(current, ConfigurationInventoryGovernanceStatus.OWNER_REVIEWED, current.classifiedBy(), current.classifiedAt(),
            actor, now(), null, null, null, null, request.reason());
        store.save(next, current.version(), "OWNER_REVIEWED", actor, request.reason(), "{}"); return detail(key);
    }

    public InventoryDetail architectureApprove(String key, ReviewRequest request, String actor) {
        requireReason(request.reason());
        ConfigurationInventoryObservation observation = observation(key); ConfigurationInventoryGovernance current = governance(key);
        assertSource(observation, request.expectedSourceObservationHash()); assertVersion(current, request.expectedVersion());
        requireState(current, ConfigurationInventoryGovernanceStatus.OWNER_REVIEWED);
        if (actor.equals(current.ownerReviewedBy())) throw new IllegalStateException("CONFIGURATION_GOVERNANCE_SOD_OWNER_ARCHITECTURE");
        ConfigurationInventoryGovernance next = copy(current, ConfigurationInventoryGovernanceStatus.ARCHITECTURE_APPROVED, current.classifiedBy(), current.classifiedAt(),
            current.ownerReviewedBy(), current.ownerReviewedAt(), actor, now(), null, null, request.reason());
        store.save(next, current.version(), "ARCHITECTURE_APPROVED", actor, request.reason(), "{}"); return detail(key);
    }

    public InventoryDetail authorizeMigration(String key, ReviewRequest request, String actor) {
        requireReason(request.reason());
        ConfigurationInventoryObservation observation = observation(key); ConfigurationInventoryGovernance current = governance(key);
        assertSource(observation, request.expectedSourceObservationHash()); assertVersion(current, request.expectedVersion());
        requireState(current, ConfigurationInventoryGovernanceStatus.ARCHITECTURE_APPROVED);
        if (actor.equals(current.architectureApprovedBy())) throw new IllegalStateException("CONFIGURATION_GOVERNANCE_SOD_ARCHITECTURE_MIGRATION");
        RuntimeConfigurationDefinition definition = definitions.findByKey(key).orElse(null);
        Readiness readiness = readiness(observation, current, definition);
        if (!readiness.ready()) throw new IllegalStateException("CONFIGURATION_MIGRATION_NOT_READY: " + String.join("; ", readiness.blockers()));
        ConfigurationInventoryGovernance next = copy(current, ConfigurationInventoryGovernanceStatus.MIGRATION_READY, current.classifiedBy(), current.classifiedAt(),
            current.ownerReviewedBy(), current.ownerReviewedAt(), current.architectureApprovedBy(), current.architectureApprovedAt(), actor, now(), request.reason());
        store.save(next, current.version(), "MIGRATION_READY", actor, request.reason(), "{\"definitionSource\":\"" + escape(definition.sourceRef()) + "\"}");
        return detail(key);
    }

    private InventorySummary summary(ConfigurationInventoryObservation observation) {
        ConfigurationInventoryGovernance g = governance(observation.configurationKey());
        Map<String,Object> advisory = map(observation.advisoryClassificationJson());
        return new InventorySummary(observation.configurationKey(), observation.namespace(), g.status().name(), observation.sourceObservationHash(),
            observation.mutabilityFloor(), list(observation.reviewFlagsJson()), advisory, g.domainOwner(), g.authorityClass(), g.scope(), g.risk(),
            g.mutability(), g.consumerContract(), g.version());
    }

    private Readiness readiness(ConfigurationInventoryObservation o, ConfigurationInventoryGovernance g, RuntimeConfigurationDefinition d) {
        List<String> blockers = new ArrayList<>();
        if (!Objects.equals(o.sourceObservationHash(), g.sourceObservationHash())) blockers.add("SOURCE_OBSERVATION_DRIFT");
        if (g.status().ordinal() < ConfigurationInventoryGovernanceStatus.ARCHITECTURE_APPROVED.ordinal()) blockers.add("ARCHITECTURE_APPROVAL_REQUIRED");
        if (!"RUNTIME_TUNABLE".equals(g.authorityClass())) blockers.add("GENERIC_RUNTIME_MIGRATION_REQUIRES_RUNTIME_TUNABLE");
        List<String> flags = list(o.reviewFlagsJson());
        if (flags.contains("PROD_PROFILE_TEST_CLASSIFICATION_CONFLICT")) blockers.add("PROD_PROFILE_TEST_CLASSIFICATION_CONFLICT");
        if (flags.contains("SPRING_CONDITION_USAGE") && !"RESTART_REQUIRED".equals(g.mutability())) blockers.add("SPRING_CONDITION_REQUIRES_RESTART_OR_REFACTOR");
        if (flags.contains("STATIC_SCHEDULE_USAGE") && !"RESTART_REQUIRED".equals(g.mutability()) && !"DYNAMIC_SCHEDULER".equals(g.consumerContract())) blockers.add("STATIC_SCHEDULE_REQUIRES_DYNAMIC_SCHEDULER_OR_RESTART");
        if (flags.contains("STARTUP_BINDING_USAGE") && !"RESTART_REQUIRED".equals(g.mutability()) && "STARTUP_BINDING".equals(g.consumerContract())) blockers.add("STARTUP_BINDING_REFACTOR_REQUIRED");
        if (d == null) blockers.add("SOURCE_CONTROLLED_DEFINITION_REQUIRED");
        else {
            if (!d.migrationAuthorized()) blockers.add("DEFINITION_MIGRATION_AUTHORIZATION_REQUIRED");
            if (!equals(d.domainOwner(), g.domainOwner())) blockers.add("DEFINITION_DOMAIN_OWNER_MISMATCH");
            if (!equals(d.authorityClass(), g.authorityClass())) blockers.add("DEFINITION_AUTHORITY_CLASS_MISMATCH");
            if (!equals(d.scope(), g.scope())) blockers.add("DEFINITION_SCOPE_MISMATCH");
            if (!equals(d.risk(), g.risk())) blockers.add("DEFINITION_RISK_MISMATCH");
            if (!equals(d.mutability(), g.mutability())) blockers.add("DEFINITION_MUTABILITY_MISMATCH");
            if (!equals(d.consumerContract(), g.consumerContract())) blockers.add("DEFINITION_CONSUMER_CONTRACT_MISMATCH");
            if (d.adminEditable() != Boolean.TRUE.equals(g.adminEditable())) blockers.add("DEFINITION_ADMIN_EDITABLE_MISMATCH");
            if (d.requiresApproval() != Boolean.TRUE.equals(g.requiresApproval())) blockers.add("DEFINITION_APPROVAL_POLICY_MISMATCH");
        }
        return new Readiness(blockers.isEmpty(), blockers);
    }

    private ConfigurationInventoryGovernance copy(ConfigurationInventoryGovernance c, ConfigurationInventoryGovernanceStatus status,
            String classifiedBy, OffsetDateTime classifiedAt, String ownerReviewedBy, OffsetDateTime ownerReviewedAt,
            String architectureApprovedBy, OffsetDateTime architectureApprovedAt, String migrationAuthorizedBy, OffsetDateTime migrationAuthorizedAt,
            String reason) {
        return new ConfigurationInventoryGovernance(c.configurationKey(), status, c.sourceObservationHash(), c.domainOwner(), c.authorityClass(), c.scope(), c.risk(),
            c.mutability(), c.consumerContract(), c.adminEditable(), c.requiresApproval(), classifiedBy, classifiedAt, ownerReviewedBy, ownerReviewedAt,
            architectureApprovedBy, architectureApprovedAt, migrationAuthorizedBy, migrationAuthorizedAt, reason.trim(), c.version(), c.updatedAt());
    }

    private ConfigurationInventoryObservation observation(String key) { return store.findObservation(key).orElseThrow(() -> new IllegalArgumentException("Configuration observation not found: " + key)); }
    private ConfigurationInventoryGovernance governance(String key) { return store.findGovernance(key).orElseThrow(() -> new IllegalArgumentException("Configuration governance state not found: " + key)); }
    private void assertSource(ConfigurationInventoryObservation o, String expected) { if (!o.sourceObservationHash().equals(required(expected,"expectedSourceObservationHash"))) throw new IllegalStateException("SOURCE_OBSERVATION_DRIFT"); }
    private static void assertVersion(ConfigurationInventoryGovernance g, long expected) { if (g.version() != expected) throw new IllegalStateException("CONFIGURATION_GOVERNANCE_VERSION_CONFLICT"); }
    private static void requireState(ConfigurationInventoryGovernance g, ConfigurationInventoryGovernanceStatus... allowed) { for (var a: allowed) if (g.status()==a) return; throw new IllegalStateException("CONFIGURATION_GOVERNANCE_STATE_CONFLICT: " + g.status()); }
    private static void requireReason(String reason) { required(reason,"reason"); }
    private static String required(String v,String name) { if(v==null||v.isBlank()) throw new IllegalArgumentException(name+" is required"); return v.trim(); }
    private static <E extends Enum<E>> String enumName(Class<E> type,String value,String name) { try{return Enum.valueOf(type,required(value,name)).name();}catch(RuntimeException ex){throw new IllegalArgumentException("Invalid "+name+": "+value);} }
    private static ConfigurationInventoryGovernanceStatus optionalStatus(String value) { return value==null||value.isBlank()?null:ConfigurationInventoryGovernanceStatus.valueOf(value.trim()); }
    private static OffsetDateTime now(){ return OffsetDateTime.now(ZoneOffset.UTC); }
    private static boolean equals(String a,String b){ return Objects.equals(a,b); }
    private static String escape(String value){ return value==null?"":value.replace("\\","\\\\").replace("\"","\\\""); }
    private List<String> list(String json){ try{return json==null?List.of():objectMapper.readValue(json,new TypeReference<List<String>>(){});}catch(Exception ex){throw new IllegalStateException("Invalid inventory JSON",ex);} }
    private Map<String,Object> map(String json){ try{return json==null?Map.of():objectMapper.readValue(json,new TypeReference<Map<String,Object>>(){});}catch(Exception ex){return Map.of();} }

    private GovernanceView governanceView(ConfigurationInventoryGovernance g){return new GovernanceView(g.status().name(),g.sourceObservationHash(),g.domainOwner(),g.authorityClass(),g.scope(),g.risk(),g.mutability(),g.consumerContract(),g.adminEditable(),g.requiresApproval(),g.classifiedBy(),g.classifiedAt(),g.ownerReviewedBy(),g.ownerReviewedAt(),g.architectureApprovedBy(),g.architectureApprovedAt(),g.migrationAuthorizedBy(),g.migrationAuthorizedAt(),g.reason(),g.version());}
    private EventView eventView(ConfigurationInventoryGovernanceEvent e){return new EventView(e.eventId(),e.eventType(),e.fromStatus(),e.toStatus(),e.actor(),e.reason(),e.sourceObservationHash(),map(e.detailJson()),e.createdAt());}
    private DefinitionView definitionView(RuntimeConfigurationDefinition d){return d==null?null:new DefinitionView(d.key(),d.sourceRef(),d.domainOwner(),d.authorityClass(),d.scope(),d.risk(),d.mutability(),d.consumerContract(),d.adminEditable(),d.requiresApproval(),d.migrationAuthorized());}

    public record InventorySummary(String key,String namespace,String status,String sourceObservationHash,String mutabilityFloor,List<String> reviewFlags,Map<String,Object> advisoryClassification,String domainOwner,String authorityClass,String scope,String risk,String mutability,String consumerContract,long version){}
    public record InventoryDetail(InventorySummary observation,GovernanceView governance,Readiness readiness,DefinitionView definition,List<EventView> events){}
    public record GovernanceView(String status,String sourceObservationHash,String domainOwner,String authorityClass,String scope,String risk,String mutability,String consumerContract,Boolean adminEditable,Boolean requiresApproval,String classifiedBy,OffsetDateTime classifiedAt,String ownerReviewedBy,OffsetDateTime ownerReviewedAt,String architectureApprovedBy,OffsetDateTime architectureApprovedAt,String migrationAuthorizedBy,OffsetDateTime migrationAuthorizedAt,String reason,long version){}
    public record Readiness(boolean ready,List<String> blockers){}
    public record DefinitionView(String key,String sourceRef,String domainOwner,String authorityClass,String scope,String risk,String mutability,String consumerContract,boolean adminEditable,boolean requiresApproval,boolean migrationAuthorized){}
    public record EventView(long eventId,String eventType,String fromStatus,String toStatus,String actor,String reason,String sourceObservationHash,Map<String,Object> detail,OffsetDateTime createdAt){}
    public record ClassificationRequest(String authorityClass,String domainOwner,String scope,String risk,String mutability,String consumerContract,Boolean adminEditable,Boolean requiresApproval,String expectedSourceObservationHash,long expectedVersion,String reason){}
    public record ReviewRequest(String expectedSourceObservationHash,long expectedVersion,String reason){}
}
