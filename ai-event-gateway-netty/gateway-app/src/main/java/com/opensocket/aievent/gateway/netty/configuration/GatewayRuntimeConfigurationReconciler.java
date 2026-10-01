package com.opensocket.aievent.gateway.netty.configuration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.opensocket.aievent.gateway.netty.config.GatewayProperties;

import jakarta.annotation.PreDestroy;

/**
 * Periodic authority reconciliation heals lost Redis/pub-sub events without DB credentials.
 * Fresh Core snapshots are authenticated, persisted as signed LKG, then atomically installed.
 */
@Component
@ConditionalOnProperty(prefix="gateway.runtime-configuration",name="enabled",havingValue="true")
public class GatewayRuntimeConfigurationReconciler {
    private static final Logger log=LoggerFactory.getLogger(GatewayRuntimeConfigurationReconciler.class);
    private static final int AUTHORITY_CONTRACT_VERSION = 2;
    private final RestClient restClient;
    private final GatewayRuntimeConfigurationProperties properties;
    private final GatewayProperties gateway;
    private final GatewayRuntimeConfigurationSnapshotVerifier verifier;
    private final GatewayRuntimeConfigurationLocalRegistry registry;
    private final GatewayRuntimeConfigurationLkgStore lkgStore;
    private final GatewayRuntimeConfigurationRecoveryTracker recoveryTracker;
    private final TaskScheduler taskScheduler;
    private volatile ScheduledFuture<?> periodicReconcileTask;

    public GatewayRuntimeConfigurationReconciler(@Qualifier("coreOutboundRestClient") RestClient restClient,
            GatewayRuntimeConfigurationProperties properties,GatewayProperties gateway,
            GatewayRuntimeConfigurationLocalRegistry registry,GatewayRuntimeConfigurationLkgStore lkgStore,
            GatewayRuntimeConfigurationRecoveryTracker recoveryTracker,TaskScheduler taskScheduler) {
        this.restClient=restClient; this.properties=properties; this.gateway=gateway; this.registry=registry;
        this.lkgStore=lkgStore; this.recoveryTracker=recoveryTracker; this.taskScheduler=taskScheduler; properties.requireSecureKey();
        this.verifier=new GatewayRuntimeConfigurationSnapshotVerifier(properties.hmacKey());
    }

    /**
     * Periodic healing starts only after all ApplicationRunner cold-start guards have completed.
     * This prevents Spring's scheduling infrastructure from racing Core readiness during bootstrap.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void startPeriodicReconciliation() {
        if(periodicReconcileTask!=null&&!periodicReconcileTask.isCancelled()) return;
        long delayMs=properties.reconcileMs();
        periodicReconcileTask=taskScheduler.scheduleWithFixedDelay(
                this::reconcileSafely,
                Instant.now().plusMillis(delayMs),
                Duration.ofMillis(delayMs));
    }

    public void reconcile() { for(String configSetId:activeConfigSetIds()) reconcileOne(configSetId); }

    private void reconcileSafely() {
        try {
            reconcile();
        } catch(RuntimeException authorityUnavailable) {
            log.warn("RUNTIME_CONFIGURATION_RECONCILE_DEFERRED coreAuthorityUnavailable={} message={}",
                    authorityUnavailable.getClass().getSimpleName(),authorityUnavailable.getMessage());
        }
    }

    @PreDestroy
    public synchronized void stopPeriodicReconciliation() {
        if(periodicReconcileTask!=null) periodicReconcileTask.cancel(false);
        periodicReconcileTask=null;
    }

    public List<String> activeConfigSetIds() {
        List<String> configured=properties.configSetIds();
        if(!configured.isEmpty()) return configured;
        String uri=properties.baseUrl()+"/internal/runtime-configuration/config-sets?nodeId="+enc(gateway.nodeId())+
                "&nodeRole=GATEWAY&nodeInstanceId="+enc(properties.nodeInstanceId())+"&authorityContractVersion="+AUTHORITY_CONTRACT_VERSION;
        RestClient.RequestHeadersSpec<?> req=restClient.get().uri(uri);
        if(!properties.authToken().isBlank()) req=req.header(properties.authHeaderName(),properties.authToken());
        String[] discovered=req.retrieve().body(String[].class);
        return discovered==null?List.of():Arrays.stream(discovered).filter(v->v!=null&&!v.isBlank()).distinct().toList();
    }

    public void reconcileOne(String configSetId) {
        GatewayRuntimeConfigurationSnapshot desired=null;
        try {
            desired=getDesired(configSetId);
            verifier.verify(desired,canonicalEnvironment(gateway.environment()));
            if(properties.lkgEnabled()) lkgStore.save(desired);
            GatewayRuntimeConfigurationSnapshot applied=registry.atomicSwap(desired);
            if(!sameEffectiveSnapshot(applied,desired)) throw new IllegalStateException("STALE_SNAPSHOT_REJECTED");
            recoveryTracker.mark(desired.configSetId(),desired.setKey(),desired.revisionId(),
                    GatewayRuntimeConfigurationSnapshotState.ACTIVE,"CORE_LIVE",null);
            acknowledge(desired,true,desired.revisionId(),null,null);
            safeReportAuthorityHealth(desired.configSetId(),GatewayRuntimeConfigurationSnapshotState.ACTIVE,desired.expiresAt());
        } catch(RuntimeException ex) {
            GatewayRuntimeConfigurationSnapshot current=registry.current(configSetId).orElse(null);
            GatewayRuntimeConfigurationSnapshotState state=current==null
                    ? GatewayRuntimeConfigurationSnapshotState.MISSING
                    : GatewayRuntimeConfigurationSnapshotFreshness.state(current,properties.lkgEnabled()?properties.maxStaleMs():0);
            recoveryTracker.mark(configSetId,current==null?null:current.setKey(),current==null?null:current.revisionId(),
                    state,"RECONCILE_FAILURE",ex.getClass().getSimpleName()+": "+ex.getMessage());
            if(desired!=null) acknowledge(desired,false,current==null?null:current.revisionId(),"SNAPSHOT_APPLY_FAILED",ex.getMessage());
            safeReportAuthorityHealth(configSetId,state,current==null?null:current.expiresAt());
        }
    }

    /** Restore only still-valid signed LKG files. Corrupt/expired files are never installed. */
    public int restoreLastKnownGood() {
        if(!properties.lkgEnabled()) return 0;
        int restored=0;
        for(GatewayRuntimeConfigurationLkgStore.LoadResult result:lkgStore.loadAll()) {
            if(!result.readable()) {
                recoveryTracker.mark(result.source(),null,null,GatewayRuntimeConfigurationSnapshotState.INVALID,
                        "PERSISTED_LKG",result.error());
                continue;
            }
            GatewayRuntimeConfigurationSnapshot snapshot=result.snapshot();
            try {
                GatewayRuntimeConfigurationSnapshotState state=verifier.verifyForRecovery(snapshot,
                        canonicalEnvironment(gateway.environment()),properties.maxStaleMs());
                GatewayRuntimeConfigurationSnapshot applied=registry.atomicSwap(snapshot);
                if(!sameEffectiveSnapshot(applied,snapshot)) throw new IllegalStateException("STALE_SNAPSHOT_REJECTED");
                recoveryTracker.mark(snapshot.configSetId(),snapshot.setKey(),snapshot.revisionId(),state,
                        "PERSISTED_LKG",null);
                safeReportAuthorityHealth(snapshot.configSetId(),state,snapshot.expiresAt());
                restored++;
            } catch(RuntimeException ex) {
                GatewayRuntimeConfigurationSnapshotState state=ex.getMessage()!=null&&ex.getMessage().contains("MAX_STALE")
                        ? GatewayRuntimeConfigurationSnapshotState.EXPIRED
                        : GatewayRuntimeConfigurationSnapshotState.INVALID;
                recoveryTracker.mark(snapshot.configSetId(),snapshot.setKey(),snapshot.revisionId(),state,
                        "PERSISTED_LKG",ex.getClass().getSimpleName()+": "+ex.getMessage());
            }
        }
        return restored;
    }

    public boolean usable(String configSetId) {
        GatewayRuntimeConfigurationSnapshot current=registry.current(configSetId).orElse(null);
        GatewayRuntimeConfigurationSnapshotState state=GatewayRuntimeConfigurationSnapshotFreshness.state(
                current,properties.lkgEnabled()?properties.maxStaleMs():0);
        return state==GatewayRuntimeConfigurationSnapshotState.ACTIVE||state==GatewayRuntimeConfigurationSnapshotState.STALE_LKG;
    }

    public GatewayRuntimeConfigurationLocalRegistry registry(){return registry;}

    private GatewayRuntimeConfigurationSnapshot getDesired(String configSetId) {
        String uri=properties.baseUrl()+"/internal/runtime-configuration/config-sets/"+enc(configSetId)+"/desired?nodeId="+enc(gateway.nodeId())+"&nodeRole=GATEWAY&nodeInstanceId="+enc(properties.nodeInstanceId())+"&authorityContractVersion="+AUTHORITY_CONTRACT_VERSION;
        RestClient.RequestHeadersSpec<?> req=restClient.get().uri(uri);
        if(!properties.authToken().isBlank()) req=req.header(properties.authHeaderName(),properties.authToken());
        return req.retrieve().body(GatewayRuntimeConfigurationSnapshot.class);
    }

    private void safeReportAuthorityHealth(String configSetId,GatewayRuntimeConfigurationSnapshotState state,java.time.OffsetDateTime expiresAt) {
        try {
            String uri=properties.baseUrl()+"/internal/runtime-configuration/config-sets/"+enc(configSetId)+"/authority-health?nodeRole=GATEWAY";
            AuthorityHealthReport body=new AuthorityHealthReport(gateway.nodeId(),"GATEWAY",properties.nodeInstanceId(),state.name(),expiresAt);
            RestClient.RequestBodySpec req=restClient.post().uri(uri);
            if(!properties.authToken().isBlank()) req.header(properties.authHeaderName(),properties.authToken());
            req.body(body).retrieve().toBodilessEntity();
        } catch(RuntimeException ignored) {
            // Authority-health telemetry must never turn a successfully authenticated snapshot apply into a failure.
        }
    }

    private void acknowledge(GatewayRuntimeConfigurationSnapshot desired,boolean success,String appliedRevision,String errorCode,String errorDetail) {
        String uri=properties.baseUrl()+"/internal/runtime-configuration/config-sets/"+enc(desired.configSetId())+"/ack?nodeRole=GATEWAY";
        ApplyAck body=new ApplyAck(gateway.nodeId(),"GATEWAY",properties.nodeInstanceId(),desired.revisionId(),appliedRevision,desired.snapshotFingerprint(),AUTHORITY_CONTRACT_VERSION,success,errorCode,errorDetail);
        RestClient.RequestBodySpec req=restClient.post().uri(uri);
        if(!properties.authToken().isBlank()) req.header(properties.authHeaderName(),properties.authToken());
        req.body(body).retrieve().toBodilessEntity();
    }

    static boolean sameEffectiveSnapshot(GatewayRuntimeConfigurationSnapshot a,GatewayRuntimeConfigurationSnapshot b){
        return a!=null&&b!=null&&a.revisionId().equals(b.revisionId())&&a.payloadHash().equalsIgnoreCase(b.payloadHash())
                &&a.authorityMode().equals(b.authorityMode())&&a.requiredKeys().equals(b.requiredKeys());
    }
    private static String canonicalEnvironment(String env){
        String v=env==null?"":env.trim().toUpperCase(Locale.ROOT);
        return switch(v){case "PRD","UAT","SIT","QA","DEV","LOCAL"->v;default->throw new IllegalArgumentException("OPENDISPATCH_ENVIRONMENT must be one of PRD,UAT,SIT,QA,DEV,LOCAL: "+env);};
    }
    private static String enc(String value){return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8);}
    private record ApplyAck(String nodeId,String nodeRole,String nodeInstanceId,String desiredRevisionId,String appliedRevisionId,String snapshotFingerprint,int supportedAuthorityContractVersion,boolean success,String errorCode,String errorDetail){}
    private record AuthorityHealthReport(String nodeId,String nodeRole,String nodeInstanceId,String authorityRuntimeState,java.time.OffsetDateTime snapshotExpiresAt){}
}
