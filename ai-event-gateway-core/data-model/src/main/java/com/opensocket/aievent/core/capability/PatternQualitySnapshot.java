package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.List;
/** Append-only current quality view derived from authoritative outcomes sharing the same signature. */
public record PatternQualitySnapshot(String snapshotId,String tenantId,String patternId,int windowSize,int sampleCount,double successRate,double humanAcceptanceRate,Long p95LatencyMs,List<String> outcomeIds,OffsetDateTime observedAt){public PatternQualitySnapshot{outcomeIds=outcomeIds==null?List.of():List.copyOf(outcomeIds);}}
