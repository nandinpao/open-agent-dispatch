package com.opensocket.aievent.core.issuetracking.relay;
import java.time.OffsetDateTime; import java.util.Objects;
public record RelayPolicySignal(String tenantId,String topologyId,String canonicalRelationId,RelayTopologyStatus topologyStatus,int syncedEdges,int failedEdges,int decisionEdges,OffsetDateTime occurredAt,String correlationId) {
 public RelayPolicySignal { tenantId=req(tenantId,"tenantId");topologyId=req(topologyId,"topologyId");canonicalRelationId=req(canonicalRelationId,"canonicalRelationId");topologyStatus=Objects.requireNonNull(topologyStatus,"topologyStatus is required");if(syncedEdges<0||failedEdges<0||decisionEdges<0)throw new IllegalArgumentException("counts cannot be negative");occurredAt=Objects.requireNonNull(occurredAt,"occurredAt is required");correlationId=correlationId==null?"":correlationId.trim(); }
 private static String req(String v,String n){Objects.requireNonNull(v,n+" is required");String x=v.trim();if(x.isEmpty())throw new IllegalArgumentException(n+" is required");return x;}
}
