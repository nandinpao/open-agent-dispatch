package com.opensocket.aievent.core.configuration.distribution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.kernel.configuration.ConfigurationInventoryGovernanceStatus;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverStore;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinition;
import com.opensocket.aievent.core.kernel.configuration.definition.RuntimeConfigurationDefinitionStore;
import com.opensocket.aievent.core.kernel.configuration.governance.RuntimeConfigurationGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.inventory.ConfigurationInventoryGovernanceStore;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationConfigSet;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevision;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionState;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionStore;

/**
 * Unconditional canonical projection of the desired Runtime Configuration payload.
 *
 * <p>V41 C3R2-X3 projects durable authority semantics together with values. The resulting
 * authorityMode/requiredKeys are later signed into the transport envelope, allowing Core,
 * Gateway and Worker nodes to enforce post-cutover completeness without database access.</p>
 */
@Service
public class RuntimeConfigurationSnapshotProjectionService {
    private final RuntimeConfigurationRevisionStore revisions;
    private final RuntimeConfigurationGovernanceStore emergencyGovernance;
    private final RuntimeConfigurationDefinitionStore definitions;
    private final ConfigurationInventoryGovernanceStore migrationGovernance;
    private final RuntimeConfigurationCutoverStore cutovers;
    private final ObjectMapper objectMapper;
    private final RuntimeConfigurationDistributionProperties distributionProperties;

    public RuntimeConfigurationSnapshotProjectionService(RuntimeConfigurationRevisionStore revisions,
            RuntimeConfigurationGovernanceStore emergencyGovernance,
            RuntimeConfigurationDefinitionStore definitions,
            ConfigurationInventoryGovernanceStore migrationGovernance, RuntimeConfigurationCutoverStore cutovers,
            ObjectMapper objectMapper, RuntimeConfigurationDistributionProperties distributionProperties) {
        this.revisions = revisions;
        this.emergencyGovernance = emergencyGovernance;
        this.definitions = definitions;
        this.migrationGovernance = migrationGovernance;
        this.cutovers = cutovers;
        this.objectMapper = objectMapper;
        this.distributionProperties = distributionProperties;
    }

    public String desiredPayloadHash(String configSetId) {
        return desiredProjection(configSetId).payloadHash();
    }

    public String desiredSnapshotFingerprint(String configSetId) {
        return desiredProjection(configSetId).snapshotFingerprint(distributionProperties.authorityContractVersion());
    }

    public SnapshotProjection desiredProjection(String configSetId) {
        String active = revisions.findActiveRevisionId(configSetId)
                .orElseThrow(() -> new IllegalArgumentException("No active runtime configuration revision for Config Set: " + configSetId));
        return projection(configSetId, active);
    }

    public SnapshotProjection projection(String configSetId, String revisionId) {
        RuntimeConfigurationConfigSet set = revisions.findConfigSet(configSetId)
                .orElseThrow(() -> new IllegalArgumentException("Config Set not found: " + configSetId));
        RuntimeConfigurationRevision revision = revisions.findRevision(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
        if (!revision.configSetId().equals(configSetId)) throw new IllegalArgumentException("Revision belongs to another Config Set");
        if (revision.state() != RuntimeConfigurationRevisionState.PUBLISHED)
            throw new IllegalStateException("Only the currently published revision may be projected: " + revisionId + " state=" + revision.state());
        String active = revisions.findActiveRevisionId(configSetId).orElse(null);
        if (!revisionId.equals(active)) throw new IllegalStateException("Stale revision is not the desired revision: " + revisionId + " active=" + active);

        TreeMap<String, JsonNode> values = new TreeMap<>();
        revisions.listItems(revisionId).forEach(item -> values.put(item.definitionKey(), parse(item.valueJson())));
        emergencyGovernance.listActiveEmergencyOverrides(configSetId)
                .forEach(override -> values.put(override.definitionKey(), parse(override.valueJson())));

        AuthorityProjection authority = authorityProjection(set.configSetId(), set.setKey(), revisionId, values.keySet());
        String payload = write(values);
        return new SnapshotProjection(set, revision, authority.mode(), authority.requiredKeys(),
                payload, sha256(payload), values.size());
    }

    private AuthorityProjection authorityProjection(String configSetId, String setKey, String revisionId, Set<String> payloadKeys) {
        var prepared=cutovers.findPrepared(configSetId);
        if(prepared.isPresent()) {
            var plan=prepared.get();
            if(!revisionId.equals(plan.revisionId())) throw new IllegalStateException("CONFIGURATION_CUTOVER_STALE_PREPARED_REVISION setKey="+setKey+" plan="+plan.revisionId()+" active="+revisionId);
            if(!payloadKeys.containsAll(plan.requiredKeys())) {
                java.util.LinkedHashSet<String> missing=new java.util.LinkedHashSet<>(plan.requiredKeys());missing.removeAll(payloadKeys);
                throw new IllegalStateException("CONFIGURATION_CUTOVER_PREPARED_PAYLOAD_INCOMPLETE setKey="+setKey+" missing="+missing);
            }
            return new AuthorityProjection(plan.targetAuthorityMode(),plan.requiredKeys());
        }
        List<RuntimeConfigurationDefinition> scoped = definitions.listAll().stream()
                .filter(RuntimeConfigurationDefinition::migrationAuthorized)
                .filter(d -> setKey.equals(d.configSetKey()))
                .toList();
        if (scoped.isEmpty()) return new AuthorityProjection("STARTUP_ONLY", Set.of());

        LinkedHashSet<String> required = new LinkedHashSet<>();
        int migrationReadyOrBeyond = 0;
        int runtimeOnly = 0;
        for (RuntimeConfigurationDefinition definition : scoped) {
            ConfigurationInventoryGovernanceStatus status = migrationGovernance.findGovernance(definition.key())
                    .map(g -> g.status()).orElse(ConfigurationInventoryGovernanceStatus.DISCOVERED);
            if (status.ordinal() >= ConfigurationInventoryGovernanceStatus.MIGRATION_READY.ordinal()) migrationReadyOrBeyond++;
            if (status == ConfigurationInventoryGovernanceStatus.MIGRATED
                    || status == ConfigurationInventoryGovernanceStatus.LEGACY_RETIRED) {
                runtimeOnly++;
                required.add(definition.key());
            }
        }
        if (runtimeOnly == scoped.size() && new java.util.TreeSet<>(required).equals(new java.util.TreeSet<>(payloadKeys))) return new AuthorityProjection("RUNTIME_ONLY", Set.copyOf(required));
        if (migrationReadyOrBeyond > 0) return new AuthorityProjection("DUAL_READ", Set.copyOf(required));
        return new AuthorityProjection("STARTUP_ONLY", Set.of());
    }

    private JsonNode parse(String json) {
        try { return objectMapper.readTree(json); }
        catch (Exception e) { throw new IllegalStateException("Stored runtime configuration value is not valid JSON", e); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Unable to serialize runtime configuration snapshot projection", e); }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record AuthorityProjection(String mode, Set<String> requiredKeys) {}

    public record SnapshotProjection(RuntimeConfigurationConfigSet configSet,
            RuntimeConfigurationRevision revision, String authorityMode, Set<String> requiredKeys,
            String payloadJson, String payloadHash, int keyCount) {
        public String snapshotFingerprint(int authorityContractVersion){return authorityContractVersion<=1?payloadHash:sha256(payloadHash+"\n"+authorityMode+"\n"+String.join(",",new java.util.TreeSet<>(requiredKeys)));}
    }
}
