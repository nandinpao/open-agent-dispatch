package com.opensocket.aievent.core.issuetracking.recovery;
import java.time.OffsetDateTime;
public record ProviderHealthState(String tenantId,String healthId,String connectionId,String projectMappingId,ProviderHealthStatus status,int consecutiveFailures,int successCount,int failureCount,int queueDepth,int maxQueueDepth,OffsetDateTime openedAt,OffsetDateTime retryAfterAt,String lastFailureCode,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt,String correlationId){
 public ProviderHealthState { if(blank(tenantId)||blank(healthId)||blank(connectionId)||status==null||consecutiveFailures<0||successCount<0||failureCount<0||queueDepth<0||maxQueueDepth<1||version<1) throw new IllegalArgumentException("Invalid provider health state."); }
 private static boolean blank(String v){return v==null||v.isBlank();}
}
