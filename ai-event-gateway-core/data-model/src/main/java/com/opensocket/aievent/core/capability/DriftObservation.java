package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.List;
/** Append-only drift decision. Results: NO_DRIFT, DRIFT_DETECTED, INSUFFICIENT_EVIDENCE. Drift may degrade ACTIVE, never promote a pattern. */
public record DriftObservation(String driftId,String tenantId,String patternId,String result,List<String> reasonCodes,String qualitySnapshotId,boolean patternDegraded,OffsetDateTime observedAt){public DriftObservation{reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes);}}
