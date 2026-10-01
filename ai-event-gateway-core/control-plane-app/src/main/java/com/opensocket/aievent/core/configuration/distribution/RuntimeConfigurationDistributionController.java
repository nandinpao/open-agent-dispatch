package com.opensocket.aievent.core.configuration.distribution;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationNodeApplyState;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationRequiredNodeTarget;
import com.opensocket.aievent.core.security.CoreInternalSecurityProperties;
import com.opensocket.aievent.core.security.CoreInternalSecurityRole;

/** Internal node reconcile/ACK API. This is not an Admin configuration editor. */
@RestController
@RequestMapping("/internal/runtime-configuration")
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationDistributionController {
    private final RuntimeConfigurationSnapshotService snapshots;
    private final RuntimeConfigurationDistributionStore store;
    private final String environment;

    public RuntimeConfigurationDistributionController(RuntimeConfigurationSnapshotService snapshots,RuntimeConfigurationDistributionStore store,
            @Value("${opendispatch.environment}") String environment) {
        this.snapshots=snapshots; this.store=store;
        this.environment=OpenDispatchEnvironment.parseCanonical(environment).name();
    }

    /** Node-side discovery removes Config Set UUIDs from deployment configuration. */
    @GetMapping("/config-sets")
    public List<String> activeConfigSets(@RequestParam String nodeId,@RequestParam String nodeRole,
            @RequestParam String nodeInstanceId,@RequestParam(required=false) Integer authorityContractVersion,Authentication authentication) {
        String role=authorizeNodeRole(authentication,nodeRole);
        List<String> ids=store.listActiveConfigSetIds(environment);
        int supported=authorityContractVersion==null?1:authorityContractVersion;
        ids.forEach(id->store.registerRequiredTarget(id,nodeId,role,nodeInstanceId,"NODE_DISCOVERY",supported));
        return ids;
    }

    @GetMapping("/config-sets/{configSetId}/desired")
    public ResponseEntity<RuntimeConfigurationSnapshotEnvelope> desired(@PathVariable String configSetId,
            @RequestParam String nodeId,@RequestParam String nodeRole,@RequestParam String nodeInstanceId,
            @RequestParam(required=false) Integer authorityContractVersion,Authentication authentication) {
        String role=authorizeNodeRole(authentication,nodeRole);
        int supported=authorityContractVersion==null?1:authorityContractVersion;
        store.registerRequiredTarget(configSetId,nodeId,role,nodeInstanceId,"NODE_DESIRED_REQUEST",supported);
        RuntimeConfigurationSnapshotEnvelope envelope=snapshots.desiredSnapshot(configSetId,role,supported);
        store.recordDesired(configSetId,nodeId,role,nodeInstanceId,envelope.revisionId(),envelope.snapshotFingerprint(),false);
        return ResponseEntity.ok(envelope);
    }

    @PostMapping("/config-sets/{configSetId}/ack")
    public ResponseEntity<RuntimeConfigurationNodeApplyState> acknowledge(@PathVariable String configSetId,
            @RequestParam String nodeRole,@RequestBody ApplyAck request,Authentication authentication) {
        String role=authorizeNodeRole(authentication,nodeRole);
        if(request.nodeRole()==null||!role.equalsIgnoreCase(request.nodeRole().trim())) return ResponseEntity.badRequest().build();
        store.registerRequiredTarget(configSetId,request.nodeId(),role,request.nodeInstanceId(),"NODE_ACK",
                request.supportedAuthorityContractVersion()<=0?1:request.supportedAuthorityContractVersion());
        int supported=request.supportedAuthorityContractVersion()<=0?1:request.supportedAuthorityContractVersion();
        RuntimeConfigurationSnapshotEnvelope desired=snapshots.desiredSnapshot(configSetId,role,supported);
        if(!desired.revisionId().equals(request.desiredRevisionId()))
            return ResponseEntity.status(409).build();
        if(!desired.snapshotFingerprint().equalsIgnoreCase(request.snapshotFingerprint()))
            return ResponseEntity.status(409).build();
        if(request.success()) {
            store.acknowledgeApplied(configSetId,request.nodeId(),role,request.nodeInstanceId(),request.desiredRevisionId(),
                    request.appliedRevisionId(),request.snapshotFingerprint());
        } else {
            store.acknowledgeFailed(configSetId,request.nodeId(),role,request.nodeInstanceId(),request.desiredRevisionId(),
                    request.appliedRevisionId(),request.snapshotFingerprint(),request.errorCode(),request.errorDetail());
        }
        return ResponseEntity.ok(store.findApplyState(configSetId,request.nodeId()).orElseThrow());
    }

    @PostMapping("/config-sets/{configSetId}/authority-health")
    public ResponseEntity<Void> authorityHealth(@PathVariable String configSetId,@RequestParam String nodeRole,
            @RequestBody AuthorityHealthReport request,Authentication authentication) {
        String role=authorizeNodeRole(authentication,nodeRole);
        if(request.nodeRole()==null||!role.equalsIgnoreCase(request.nodeRole().trim())) return ResponseEntity.badRequest().build();
        store.recordAuthorityHealth(configSetId,request.nodeId(),role,request.nodeInstanceId(),
                request.authorityRuntimeState(),request.snapshotExpiresAt());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/config-sets/{configSetId}/apply-states")
    public List<RuntimeConfigurationNodeApplyState> applyStates(@PathVariable String configSetId) {
        return store.listRequiredApplyStates(configSetId);
    }

    @GetMapping("/config-sets/{configSetId}/required-targets")
    public List<RuntimeConfigurationRequiredNodeTarget> requiredTargets(@PathVariable String configSetId,Authentication authentication) {
        requireOperator(authentication);
        return store.listRequiredTargets(configSetId);
    }

    @PostMapping("/config-sets/{configSetId}/required-targets")
    public RuntimeConfigurationRequiredNodeTarget registerRequiredTarget(@PathVariable String configSetId,
            @RequestBody RequiredTargetRegistration request,Authentication authentication) {
        requireOperator(authentication);
        store.registerRequiredTarget(configSetId,request.nodeId(),request.nodeRole(),request.nodeInstanceId(),
                request.registrationSource()==null||request.registrationSource().isBlank()?"OPERATOR_TOPOLOGY":request.registrationSource(),
                request.supportedAuthorityContractVersion()<=0?1:request.supportedAuthorityContractVersion());
        return store.listRequiredTargets(configSetId).stream()
                .filter(target->target.nodeId().equals(request.nodeId()))
                .findFirst().orElseThrow();
    }

    private String authorizeNodeRole(Authentication authentication,String requested) {
        String role=requested==null?"":requested.trim().toUpperCase();
        boolean gateway=has(authentication,CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.GATEWAY));
        boolean worker=has(authentication,CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.ADAPTER_WORKER));
        boolean operator=has(authentication,CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.OPERATOR));
        if((role.equals("GATEWAY")&&gateway)||(role.equals("WORKER")&&worker)||(role.equals("CORE")&&operator)) return role;
        throw new org.springframework.security.access.AccessDeniedException("Internal principal cannot acknowledge runtime configuration for nodeRole="+role);
    }
    private void requireOperator(Authentication authentication) {
        if(!has(authentication,CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.OPERATOR)))
            throw new org.springframework.security.access.AccessDeniedException("OPERATOR role is required for Runtime Configuration topology management");
    }
    private static boolean has(Authentication auth,String authority){return auth!=null&&auth.getAuthorities().stream().anyMatch(a->authority.equals(a.getAuthority()));}

    public record RequiredTargetRegistration(String nodeId,String nodeRole,String nodeInstanceId,String registrationSource,int supportedAuthorityContractVersion) {}
    public record AuthorityHealthReport(String nodeId,String nodeRole,String nodeInstanceId,String authorityRuntimeState,
            java.time.OffsetDateTime snapshotExpiresAt) {}
    public record ApplyAck(String nodeId,String nodeRole,String nodeInstanceId,String desiredRevisionId,String appliedRevisionId,
            String snapshotFingerprint,int supportedAuthorityContractVersion,boolean success,String errorCode,String errorDetail) {}
}
