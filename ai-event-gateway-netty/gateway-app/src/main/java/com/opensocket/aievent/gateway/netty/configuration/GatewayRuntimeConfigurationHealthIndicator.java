package com.opensocket.aievent.gateway.netty.configuration;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness-oriented authority health. STALE_LKG stays available until max-stale is exceeded. */
@Component("runtimeConfigurationAuthority")
@ConditionalOnProperty(prefix="gateway.runtime-configuration",name="enabled",havingValue="true")
public final class GatewayRuntimeConfigurationHealthIndicator implements HealthIndicator {
    private final GatewayRuntimeConfigurationProperties properties;
    private final GatewayRuntimeConfigurationLocalRegistry registry;
    private final GatewayRuntimeConfigurationRecoveryTracker tracker;

    public GatewayRuntimeConfigurationHealthIndicator(GatewayRuntimeConfigurationProperties properties,
            GatewayRuntimeConfigurationLocalRegistry registry,
            GatewayRuntimeConfigurationRecoveryTracker tracker) {
        this.properties=properties;this.registry=registry;this.tracker=tracker;
    }

    @Override public Health health() {
        Map<String,Object> sets=new LinkedHashMap<>();
        boolean healthy=true;
        int active=0,stale=0,expired=0;
        for(var entry:registry.snapshots().entrySet()) {
            var snapshot=entry.getValue();
            var state=GatewayRuntimeConfigurationSnapshotFreshness.state(snapshot,
                    properties.lkgEnabled()?properties.maxStaleMs():0);
            if(state==GatewayRuntimeConfigurationSnapshotState.ACTIVE)active++;
            else if(state==GatewayRuntimeConfigurationSnapshotState.STALE_LKG)stale++;
            else {expired++;healthy=false;}
            sets.put(entry.getKey(),Map.of(
                    "setKey",snapshot.setKey(),"revisionId",snapshot.revisionId(),"authorityMode",snapshot.authorityMode(),
                    "state",state.name(),"expiresAt",snapshot.expiresAt().toString(),"fingerprint",snapshot.snapshotFingerprint()));
        }
        if(properties.failClosedOnColdStart()&&sets.isEmpty())healthy=false;
        Map<String,Object> details=new LinkedHashMap<>();
        details.put("authority","CORE_SIGNED_SNAPSHOT");
        details.put("lkgEnabled",properties.lkgEnabled());
        details.put("maxStaleMs",properties.maxStaleMs());
        details.put("lkgDirectory",properties.lkgDirectory());
        details.put("active",active);details.put("staleLkg",stale);details.put("expired",expired);
        details.put("configSets",sets);
        details.put("recoveryEvents",tracker.snapshot());
        return healthy?Health.up().withDetails(details).build():Health.down().withDetails(details).build();
    }
}
