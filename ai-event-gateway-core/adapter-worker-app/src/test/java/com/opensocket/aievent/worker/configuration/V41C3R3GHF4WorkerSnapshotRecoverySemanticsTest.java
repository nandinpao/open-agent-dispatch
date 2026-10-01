package com.opensocket.aievent.worker.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.worker.AdapterWorkerProperties;
import tools.jackson.databind.json.JsonMapper;

class V41C3R3GHF4WorkerSnapshotRecoverySemanticsTest {
    @Test void freshnessTransitionsFromActiveToStaleLkgToExpired(){
        OffsetDateTime now=OffsetDateTime.of(2026,9,29,6,0,0,0,ZoneOffset.UTC);
        var active=snapshot(now,now.plusSeconds(30),"DUAL_READ",Set.of(),"{}");
        var stale=snapshot(now.minusMinutes(2),now.minusSeconds(30),"RUNTIME_ONLY",Set.of(AdapterWorkerRuntimeConfigurationView.LEASE_SECONDS),"{\"adapter-worker.lease-seconds\":90}");
        var expired=snapshot(now.minusMinutes(10),now.minusMinutes(6),"RUNTIME_ONLY",Set.of(AdapterWorkerRuntimeConfigurationView.LEASE_SECONDS),"{\"adapter-worker.lease-seconds\":90}");
        assertThat(WorkerRuntimeConfigurationSnapshotFreshness.state(active,300000,now)).isEqualTo(WorkerRuntimeConfigurationSnapshotState.ACTIVE);
        assertThat(WorkerRuntimeConfigurationSnapshotFreshness.state(stale,300000,now)).isEqualTo(WorkerRuntimeConfigurationSnapshotState.STALE_LKG);
        assertThat(WorkerRuntimeConfigurationSnapshotFreshness.state(expired,300000,now)).isEqualTo(WorkerRuntimeConfigurationSnapshotState.EXPIRED);
    }

    @Test void runtimeOnlySnapshotBeyondMaxStaleCannotReactivateStartupValue(){
        AdapterWorkerProperties startup=new AdapterWorkerProperties();startup.setLeaseSeconds(120);
        WorkerRuntimeConfigurationLocalRegistry registry=new WorkerRuntimeConfigurationLocalRegistry();
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        registry.atomicSwap(snapshot(now.minusHours(2),now.minusHours(1),"RUNTIME_ONLY",Set.of(AdapterWorkerRuntimeConfigurationView.LEASE_SECONDS),"{\"adapter-worker.lease-seconds\":90}"));
        var view=new AdapterWorkerRuntimeConfigurationView(startup,registry,JsonMapper.builder().build(),true);
        assertThatThrownBy(view::leaseSeconds).isInstanceOf(IllegalStateException.class).hasMessageContaining("RUNTIME_CONFIGURATION_SNAPSHOT_EXPIRED");
    }

    private static WorkerRuntimeConfigurationSnapshot snapshot(OffsetDateTime issued,OffsetDateTime expires,String mode,Set<String> required,String payload){
        return new WorkerRuntimeConfigurationSnapshot("LOCAL","worker-hf4",AdapterWorkerRuntimeConfigurationView.SET_KEY,"hf4-r1",1,14,issued,expires,"WORKER",mode,required,payload,"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","signature");
    }
}
