package com.opensocket.aievent.worker.configuration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.opensocket.aievent.worker.AdapterWorkerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Periodic Worker authority reconciliation with authenticated persistent LKG recovery. */
@Component
@ConditionalOnProperty(prefix="adapter-worker.runtime-configuration",name="enabled",havingValue="true")
public class WorkerRuntimeConfigurationReconciler {
    private static final int AUTHORITY_CONTRACT_VERSION = 2;
    private final RestClient restClient;
    private final AdapterWorkerProperties worker;
    private final WorkerRuntimeConfigurationProperties properties;
    private final WorkerRuntimeConfigurationLocalRegistry registry;
    private final WorkerRuntimeConfigurationSnapshotVerifier verifier;
    private final WorkerRuntimeConfigurationLkgStore lkgStore;
    private final WorkerRuntimeConfigurationRecoveryTracker recoveryTracker;
    private final String environment;

    public WorkerRuntimeConfigurationReconciler(@Qualifier("adapterWorkerCoreRestClient") RestClient restClient,
            AdapterWorkerProperties worker,WorkerRuntimeConfigurationProperties properties,
            WorkerRuntimeConfigurationLocalRegistry registry,WorkerRuntimeConfigurationLkgStore lkgStore,
            WorkerRuntimeConfigurationRecoveryTracker recoveryTracker,
            @Value("${opendispatch.environment}") String environment) {
        this.restClient=restClient;this.worker=worker;this.properties=properties;this.registry=registry;
        this.lkgStore=lkgStore;this.recoveryTracker=recoveryTracker;properties.requireSecureKey();
        this.verifier=new WorkerRuntimeConfigurationSnapshotVerifier(properties.hmacKey());this.environment=canonicalEnvironment(environment);
    }

    @Scheduled(fixedDelayString="${adapter-worker.runtime-configuration.reconcile-ms:5000}")
    public void reconcile(){for(String configSetId:activeConfigSetIds())reconcileOne(configSetId);}

    public List<String> activeConfigSetIds(){
        List<String> configured=properties.configSetIds();
        if(!configured.isEmpty())return configured;
        String uri="/internal/runtime-configuration/config-sets?nodeId="+enc(worker.getWorkerId())+
                "&nodeRole=WORKER&nodeInstanceId="+enc(properties.nodeInstanceId())+"&authorityContractVersion="+AUTHORITY_CONTRACT_VERSION;
        RestClient.RequestHeadersSpec<?> req=restClient.get().uri(uri);
        if(!worker.getToken().isBlank())req=req.header(worker.getTokenHeader(),worker.getToken());
        String[] discovered=req.retrieve().body(String[].class);
        return discovered==null?List.of():Arrays.stream(discovered).filter(v->v!=null&&!v.isBlank()).distinct().toList();
    }

    public void reconcileOne(String configSetId){
        WorkerRuntimeConfigurationSnapshot desired=null;
        try{
            desired=getDesired(configSetId);verifier.verify(desired,environment);
            if(properties.lkgEnabled())lkgStore.save(desired);
            var applied=registry.atomicSwap(desired);
            if(!sameEffectiveSnapshot(applied,desired))throw new IllegalStateException("STALE_SNAPSHOT_REJECTED");
            recoveryTracker.mark(desired.configSetId(),desired.setKey(),desired.revisionId(),WorkerRuntimeConfigurationSnapshotState.ACTIVE,"CORE_LIVE",null);
            acknowledge(desired,true,desired.revisionId(),null,null);
            safeReportAuthorityHealth(desired.configSetId(),WorkerRuntimeConfigurationSnapshotState.ACTIVE,desired.expiresAt());
        }catch(RuntimeException ex){
            var current=registry.current(configSetId).orElse(null);
            var state=current==null?WorkerRuntimeConfigurationSnapshotState.MISSING:WorkerRuntimeConfigurationSnapshotFreshness.state(current,properties.lkgEnabled()?properties.maxStaleMs():0);
            recoveryTracker.mark(configSetId,current==null?null:current.setKey(),current==null?null:current.revisionId(),state,"RECONCILE_FAILURE",ex.getClass().getSimpleName()+": "+ex.getMessage());
            if(desired!=null)acknowledge(desired,false,current==null?null:current.revisionId(),"SNAPSHOT_APPLY_FAILED",ex.getMessage());
            safeReportAuthorityHealth(configSetId,state,current==null?null:current.expiresAt());
        }
    }

    public int restoreLastKnownGood(){
        if(!properties.lkgEnabled())return 0;int restored=0;
        for(var result:lkgStore.loadAll()){
            if(!result.readable()){recoveryTracker.mark(result.source(),null,null,WorkerRuntimeConfigurationSnapshotState.INVALID,"PERSISTED_LKG",result.error());continue;}
            var snapshot=result.snapshot();
            try{
                var state=verifier.verifyForRecovery(snapshot,environment,properties.maxStaleMs());
                var applied=registry.atomicSwap(snapshot);if(!sameEffectiveSnapshot(applied,snapshot))throw new IllegalStateException("STALE_SNAPSHOT_REJECTED");
                recoveryTracker.mark(snapshot.configSetId(),snapshot.setKey(),snapshot.revisionId(),state,"PERSISTED_LKG",null);
                safeReportAuthorityHealth(snapshot.configSetId(),state,snapshot.expiresAt());restored++;
            }catch(RuntimeException ex){
                var state=ex.getMessage()!=null&&ex.getMessage().contains("MAX_STALE")?WorkerRuntimeConfigurationSnapshotState.EXPIRED:WorkerRuntimeConfigurationSnapshotState.INVALID;
                recoveryTracker.mark(snapshot.configSetId(),snapshot.setKey(),snapshot.revisionId(),state,"PERSISTED_LKG",ex.getClass().getSimpleName()+": "+ex.getMessage());
            }
        }
        return restored;
    }

    public boolean usable(String configSetId){var current=registry.current(configSetId).orElse(null);var state=WorkerRuntimeConfigurationSnapshotFreshness.state(current,properties.lkgEnabled()?properties.maxStaleMs():0);return state==WorkerRuntimeConfigurationSnapshotState.ACTIVE||state==WorkerRuntimeConfigurationSnapshotState.STALE_LKG;}
    public WorkerRuntimeConfigurationLocalRegistry registry(){return registry;}

    private WorkerRuntimeConfigurationSnapshot getDesired(String configSetId){
        String uri="/internal/runtime-configuration/config-sets/"+enc(configSetId)+"/desired?nodeId="+enc(worker.getWorkerId())+"&nodeRole=WORKER&nodeInstanceId="+enc(properties.nodeInstanceId())+"&authorityContractVersion="+AUTHORITY_CONTRACT_VERSION;
        RestClient.RequestHeadersSpec<?> req=restClient.get().uri(uri);if(!worker.getToken().isBlank())req=req.header(worker.getTokenHeader(),worker.getToken());
        return req.retrieve().body(WorkerRuntimeConfigurationSnapshot.class);
    }
    private void safeReportAuthorityHealth(String configSetId,WorkerRuntimeConfigurationSnapshotState state,java.time.OffsetDateTime expiresAt){
        try{
            String uri="/internal/runtime-configuration/config-sets/"+enc(configSetId)+"/authority-health?nodeRole=WORKER";
            var body=new AuthorityHealthReport(worker.getWorkerId(),"WORKER",properties.nodeInstanceId(),state.name(),expiresAt);
            RestClient.RequestBodySpec req=restClient.post().uri(uri);if(!worker.getToken().isBlank())req.header(worker.getTokenHeader(),worker.getToken());req.body(body).retrieve().toBodilessEntity();
        }catch(RuntimeException ignored){
            // Authority-health telemetry is best effort and never changes accepted snapshot authority.
        }
    }
    private void acknowledge(WorkerRuntimeConfigurationSnapshot desired,boolean success,String appliedRevision,String errorCode,String errorDetail){
        String uri="/internal/runtime-configuration/config-sets/"+enc(desired.configSetId())+"/ack?nodeRole=WORKER";
        var body=new ApplyAck(worker.getWorkerId(),"WORKER",properties.nodeInstanceId(),desired.revisionId(),appliedRevision,desired.snapshotFingerprint(),AUTHORITY_CONTRACT_VERSION,success,errorCode,errorDetail);
        RestClient.RequestBodySpec req=restClient.post().uri(uri);if(!worker.getToken().isBlank())req.header(worker.getTokenHeader(),worker.getToken());req.body(body).retrieve().toBodilessEntity();
    }
    static boolean sameEffectiveSnapshot(WorkerRuntimeConfigurationSnapshot a,WorkerRuntimeConfigurationSnapshot b){return a!=null&&b!=null&&a.revisionId().equals(b.revisionId())&&a.payloadHash().equalsIgnoreCase(b.payloadHash())&&a.authorityMode().equals(b.authorityMode())&&a.requiredKeys().equals(b.requiredKeys());}
    private static String canonicalEnvironment(String env){String v=env==null?"":env.trim().toUpperCase(Locale.ROOT);return switch(v){case "PRD","UAT","SIT","QA","DEV","LOCAL"->v;default->throw new IllegalArgumentException("OPENDISPATCH_ENVIRONMENT must be one of PRD,UAT,SIT,QA,DEV,LOCAL: "+env);};}
    private static String enc(String v){return URLEncoder.encode(v==null?"":v,StandardCharsets.UTF_8);}
    private record ApplyAck(String nodeId,String nodeRole,String nodeInstanceId,String desiredRevisionId,String appliedRevisionId,String snapshotFingerprint,int supportedAuthorityContractVersion,boolean success,String errorCode,String errorDetail){}
    private record AuthorityHealthReport(String nodeId,String nodeRole,String nodeInstanceId,String authorityRuntimeState,java.time.OffsetDateTime snapshotExpiresAt){}
}
