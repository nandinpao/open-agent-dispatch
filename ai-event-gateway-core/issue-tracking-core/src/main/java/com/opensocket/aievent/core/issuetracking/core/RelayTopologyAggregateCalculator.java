package com.opensocket.aievent.core.issuetracking.core;
import java.util.List; import com.opensocket.aievent.core.issuetracking.relay.*;
public final class RelayTopologyAggregateCalculator {
 public RelayTopologyStatus calculate(List<RelayEdge> edges){
  if(edges==null||edges.isEmpty())return RelayTopologyStatus.DECISION_REQUIRED;
  int synced=0,failed=0,decision=0,active=0,compensated=0;
  for(RelayEdge edge:edges){switch(edge.status()){
   case SYNCED -> synced++; case COMPENSATED,SUPERSEDED -> compensated++;
   case FAILED_PERMANENT,DEAD_LETTER -> failed++;
   case REQUESTED,READY,IN_PROGRESS,RETRY_WAITING -> active++;
  }}
  for(RelayEdge edge:edges)if(edge.lastErrorCode().equals("CONTROLLED_DECISION_REQUIRED")||edge.lastErrorCode().equals("PROVIDER_NATIVE_RELATION_UNSUPPORTED"))decision++;
  if(compensated==edges.size())return RelayTopologyStatus.COMPENSATED;
  if(decision>0)return RelayTopologyStatus.DECISION_REQUIRED;
  if(synced==edges.size())return RelayTopologyStatus.READY;
  if(synced>0||active>0||compensated>0)return RelayTopologyStatus.PARTIAL;
  if(failed==edges.size())return RelayTopologyStatus.BROKEN;
  return RelayTopologyStatus.DECISION_REQUIRED;
 }
}
