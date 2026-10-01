package com.opensocket.aievent.core.configuration.distribution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

/** Core node local snapshot apply path. V40-5 business consumers will use the registry through typed views. */
@Component
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationCoreSnapshotApplier {
    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigurationCoreSnapshotApplier.class);
    private final RuntimeConfigurationSnapshotAuthenticator authenticator;
    private final RuntimeConfigurationDistributionStore store;
    private final RuntimeConfigurationDistributionProperties properties;
    private final RuntimeConfigurationLocalSnapshotRegistry registry;
    private final String environment;
    private final ObjectMapper objectMapper;

    public RuntimeConfigurationCoreSnapshotApplier(RuntimeConfigurationSnapshotAuthenticator authenticator,
            RuntimeConfigurationDistributionStore store,RuntimeConfigurationDistributionProperties properties,
            RuntimeConfigurationLocalSnapshotRegistry registry,ObjectMapper objectMapper,
            @Value("${opendispatch.environment}") String environment) {
        this.authenticator=authenticator; this.store=store; this.properties=properties; this.registry=registry; this.objectMapper=objectMapper;
        this.environment=OpenDispatchEnvironment.parseCanonical(environment).name();
    }

    public RuntimeConfigurationSnapshotEnvelope apply(RuntimeConfigurationSnapshotEnvelope envelope) {
        String nodeId=properties.coreNodeId(); String instanceId=properties.coreNodeInstanceId();
        log.info("runtime_config_snapshot_apply_started setKey={} configSetId={} revisionId={} fingerprint={} audience={} nodeId={} instanceId={}",
                envelope.setKey(), envelope.configSetId(), envelope.revisionId(), envelope.payloadHash(), envelope.audience(), nodeId, instanceId);
        store.registerRequiredTarget(envelope.configSetId(),nodeId,"CORE",instanceId,"CORE_RUNTIME",2);
        store.recordDesired(envelope.configSetId(),nodeId,"CORE",instanceId,envelope.revisionId(),envelope.snapshotFingerprint(),true);
        try {
            if(!authenticator.verifyFor(envelope,environment,Set.of("CORE","ALL_NODES"))) throw new IllegalArgumentException("SNAPSHOT_VALIDATION_FAILED");
            validateAuthorityContract(envelope);
            RuntimeConfigurationSnapshotEnvelope applied=registry.atomicSwap(envelope);
            if(!applied.revisionId().equals(envelope.revisionId())||!applied.payloadHash().equalsIgnoreCase(envelope.payloadHash())
                    ||!applied.authorityMode().equals(envelope.authorityMode())||!applied.requiredKeys().equals(envelope.requiredKeys())) throw new IllegalStateException("STALE_SNAPSHOT_REJECTED");
            store.acknowledgeApplied(envelope.configSetId(),nodeId,"CORE",instanceId,envelope.revisionId(),envelope.revisionId(),envelope.snapshotFingerprint());
            log.info("runtime_config_snapshot_applied setKey={} configSetId={} revisionId={} fingerprint={} nodeId={} instanceId={}",
                    envelope.setKey(), envelope.configSetId(), envelope.revisionId(), envelope.payloadHash(), nodeId, instanceId);
            return applied;
        } catch(RuntimeException ex) {
            String current=registry.current(envelope.configSetId()).map(RuntimeConfigurationSnapshotEnvelope::revisionId).orElse(null);
            store.acknowledgeFailed(envelope.configSetId(),nodeId,"CORE",instanceId,envelope.revisionId(),current,envelope.snapshotFingerprint(),"SNAPSHOT_APPLY_FAILED",ex.getMessage());
            log.error("runtime_config_snapshot_apply_failed setKey={} configSetId={} desiredRevisionId={} currentRevisionId={} fingerprint={} nodeId={} instanceId={} errorType={}",
                    envelope.setKey(), envelope.configSetId(), envelope.revisionId(), current, envelope.payloadHash(), nodeId, instanceId,
                    ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void validateAuthorityContract(RuntimeConfigurationSnapshotEnvelope envelope) {
        try {
            JsonNode root=objectMapper.readTree(envelope.payloadJson());
            if(root==null||!root.isObject()) throw new IllegalArgumentException("SNAPSHOT_PAYLOAD_NOT_OBJECT");
            java.util.LinkedHashSet<String> actual=new java.util.LinkedHashSet<>();
            root.properties().forEach(e->actual.add(e.getKey()));
            if(!actual.containsAll(envelope.requiredKeys())) {
                java.util.LinkedHashSet<String> missing=new java.util.LinkedHashSet<>(envelope.requiredKeys()); missing.removeAll(actual);
                throw new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+envelope.setKey()+" missing="+missing);
            }
            if(envelope.runtimeOnly() && !new java.util.TreeSet<>(actual).equals(new java.util.TreeSet<>(envelope.requiredKeys())))
                throw new IllegalStateException("RUNTIME_CONFIG_RUNTIME_ONLY_MANIFEST_MISMATCH setKey="+envelope.setKey());
            if("STARTUP_ONLY".equals(envelope.authorityMode()) && !envelope.requiredKeys().isEmpty())
                throw new IllegalStateException("RUNTIME_CONFIG_STARTUP_ONLY_REQUIRED_KEYS_NOT_EMPTY setKey="+envelope.setKey());
        } catch(RuntimeException ex){throw ex;} catch(Exception ex){throw new IllegalStateException("SNAPSHOT_AUTHORITY_CONTRACT_INVALID",ex);}
    }

    public RuntimeConfigurationLocalSnapshotRegistry registry(){return registry;}


}
