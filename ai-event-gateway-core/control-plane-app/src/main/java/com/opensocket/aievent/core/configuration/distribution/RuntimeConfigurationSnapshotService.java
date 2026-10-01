package com.opensocket.aievent.core.configuration.distribution;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.configuration.distribution.RuntimeConfigurationSnapshotProjectionService.SnapshotProjection;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

/** Creates authenticated transport snapshots from the unconditional canonical desired projection. */
@Service
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigurationSnapshotService.class);
    private final RuntimeConfigurationSnapshotProjectionService projections;
    private final RuntimeConfigurationSnapshotAuthenticator authenticator;
    private final RuntimeConfigurationDistributionProperties properties;

    public RuntimeConfigurationSnapshotService(RuntimeConfigurationSnapshotProjectionService projections,
            RuntimeConfigurationSnapshotAuthenticator authenticator, RuntimeConfigurationDistributionProperties properties) {
        this.projections = projections;
        this.authenticator = authenticator;
        this.properties = properties;
    }

    public RuntimeConfigurationSnapshotEnvelope desiredSnapshot(String configSetId,String audience) {
        return desiredSnapshot(configSetId,audience,properties.authorityContractVersion());
    }

    /**
     * C3R3-B rolling-upgrade negotiation. A node receives no newer authority contract than it
     * explicitly declared, even when the Control Plane has already enabled v2 globally.
     */
    public RuntimeConfigurationSnapshotEnvelope desiredSnapshot(String configSetId,String audience,int nodeSupportedAuthorityContractVersion) {
        SnapshotProjection projection = projections.desiredProjection(configSetId);
        return signedEnvelope(projection, audience, negotiatedAuthorityContractVersion(nodeSupportedAuthorityContractVersion));
    }

    public RuntimeConfigurationSnapshotEnvelope snapshot(String configSetId,String revisionId,String audience) {
        return snapshot(configSetId,revisionId,audience,properties.authorityContractVersion());
    }

    public RuntimeConfigurationSnapshotEnvelope snapshot(String configSetId,String revisionId,String audience,int nodeSupportedAuthorityContractVersion) {
        SnapshotProjection projection = projections.projection(configSetId, revisionId);
        return signedEnvelope(projection, audience, negotiatedAuthorityContractVersion(nodeSupportedAuthorityContractVersion));
    }

    int negotiatedAuthorityContractVersion(int nodeSupportedAuthorityContractVersion) {
        return negotiateAuthorityContractVersion(properties.authorityContractVersion(),nodeSupportedAuthorityContractVersion);
    }

    static int negotiateAuthorityContractVersion(int controlPlaneVersion,int nodeSupportedAuthorityContractVersion) {
        int control=controlPlaneVersion<=0?1:Math.min(controlPlaneVersion,2);
        int supported=nodeSupportedAuthorityContractVersion<=0?1:Math.min(nodeSupportedAuthorityContractVersion,2);
        return Math.min(control,supported);
    }

    private RuntimeConfigurationSnapshotEnvelope signedEnvelope(SnapshotProjection projection, String audience,int authorityContractVersion) {
        OffsetDateTime issued=OffsetDateTime.now(ZoneOffset.UTC);
        String authorityMode=authorityContractVersion>=2?projection.authorityMode():"DUAL_READ";
        java.util.Set<String> requiredKeys=authorityContractVersion>=2?projection.requiredKeys():java.util.Set.of();
        RuntimeConfigurationSnapshotEnvelope unsigned=new RuntimeConfigurationSnapshotEnvelope(
                projection.configSet().environment().name(), projection.configSet().configSetId(), projection.configSet().setKey(),
                projection.revision().revisionId(), projection.revision().sequenceNo(), projection.revision().definitionSchemaVersion(),
                issued, issued.plus(properties.snapshotTtl()), safeAudience(audience), authorityContractVersion, authorityMode, requiredKeys,
                projection.payloadJson(), projection.payloadHash(), "");
        RuntimeConfigurationSnapshotEnvelope signed = new RuntimeConfigurationSnapshotEnvelope(
                unsigned.environment(),unsigned.configSetId(),unsigned.setKey(),unsigned.revisionId(),unsigned.sequenceNo(),unsigned.schemaVersion(),
                unsigned.issuedAt(),unsigned.expiresAt(),unsigned.audience(),unsigned.authorityContractVersion(),unsigned.authorityMode(),unsigned.requiredKeys(),
                unsigned.payloadJson(),unsigned.payloadHash(),authenticator.sign(unsigned.signingInput()));
        log.info("runtime_config_snapshot_created setKey={} configSetId={} revisionId={} sequenceNo={} schemaVersion={} keyCount={} authorityMode={} requiredKeyCount={} fingerprint={} audience={} expiresAt={}",
                projection.configSet().setKey(), projection.configSet().configSetId(), projection.revision().revisionId(),
                projection.revision().sequenceNo(), projection.revision().definitionSchemaVersion(), projection.keyCount(),
                signed.authorityMode(), signed.requiredKeys().size(), signed.snapshotFingerprint(), signed.audience(), signed.expiresAt());
        return signed;
    }

    private static String safeAudience(String value){return value==null||value.isBlank()?"ALL_NODES":value.trim().toUpperCase();}
}
