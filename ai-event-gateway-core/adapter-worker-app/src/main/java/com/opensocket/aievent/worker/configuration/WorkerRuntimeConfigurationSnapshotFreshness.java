package com.opensocket.aievent.worker.configuration;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class WorkerRuntimeConfigurationSnapshotFreshness {
    private WorkerRuntimeConfigurationSnapshotFreshness() {}
    public static WorkerRuntimeConfigurationSnapshotState state(WorkerRuntimeConfigurationSnapshot snapshot,long maxStaleMs){return state(snapshot,maxStaleMs,OffsetDateTime.now(ZoneOffset.UTC));}
    static WorkerRuntimeConfigurationSnapshotState state(WorkerRuntimeConfigurationSnapshot snapshot,long maxStaleMs,OffsetDateTime now){
        if(snapshot==null)return WorkerRuntimeConfigurationSnapshotState.MISSING;
        if(snapshot.expiresAt()==null)return WorkerRuntimeConfigurationSnapshotState.INVALID;
        if(snapshot.expiresAt().isAfter(now))return WorkerRuntimeConfigurationSnapshotState.ACTIVE;
        long bounded=Math.max(0L,maxStaleMs);if(bounded==0L)return WorkerRuntimeConfigurationSnapshotState.EXPIRED;
        return snapshot.expiresAt().plus(Duration.ofMillis(bounded)).isAfter(now)?WorkerRuntimeConfigurationSnapshotState.STALE_LKG:WorkerRuntimeConfigurationSnapshotState.EXPIRED;
    }
}
