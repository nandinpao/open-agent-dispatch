package com.opensocket.aievent.core.issuetracking.recovery;
import java.time.OffsetDateTime;
public record OrderedProjectionLane(String tenantId,String laneId,String aggregateKey,ProjectionLaneType laneType,String connectionId,String projectMappingId,ProjectionLaneStatus status,long nextSequence,long activeGeneration,int maxQueueDepth,int queuedCount,int inFlightCount,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt,String correlationId){
 public OrderedProjectionLane { if(blank(tenantId)||blank(laneId)||blank(aggregateKey)||laneType==null||blank(connectionId)||status==null) throw new IllegalArgumentException("Invalid ordered projection lane."); if(nextSequence<1||activeGeneration<1||maxQueueDepth<1||queuedCount<0||inFlightCount<0||version<1) throw new IllegalArgumentException("Invalid lane counters."); }
 private static boolean blank(String v){return v==null||v.isBlank();}
}
