package com.opensocket.aievent.core.issuetracking.relay;
import java.time.OffsetDateTime; import java.util.*;
public interface RelayGovernanceRepository {
 CanonicalIssueRelation saveRelation(CanonicalIssueRelation value); boolean saveRelationExpectedVersion(CanonicalIssueRelation value,long expectedVersion); Optional<CanonicalIssueRelation> findRelation(String tenantId,String relationId); List<CanonicalIssueRelation> listRelations(String tenantId,CanonicalRelationStatus status,int limit);
 RelayTopology saveTopology(RelayTopology value); boolean saveTopologyExpectedVersion(RelayTopology value,long expectedVersion); Optional<RelayTopology> findTopology(String tenantId,String topologyId); List<RelayTopology> listTopologies(String tenantId,RelayTopologyStatus status,int limit);
 RelayEdge saveEdge(RelayEdge value); boolean saveEdgeExpectedVersion(RelayEdge value,long expectedVersion); Optional<RelayEdge> findEdge(String tenantId,String edgeId); List<RelayEdge> listEdges(String tenantId,String topologyId,RelayEdgeStatus status,int limit); List<RelayEdge> claimDueEdges(String tenantId,OffsetDateTime dueAt,int limit);
 RelayCompensation saveCompensation(RelayCompensation value); boolean saveCompensationExpectedVersion(RelayCompensation value,long expectedVersion); Optional<RelayCompensation> findCompensation(String tenantId,String compensationId); List<RelayCompensation> listCompensations(String tenantId,String topologyId,RelayCompensationStatus status,int limit);
 RelayEvent appendEvent(RelayEvent value); Optional<RelayEvent> latestEvent(String tenantId,String aggregateType,String aggregateId); List<RelayEvent> listEvents(String tenantId,String aggregateType,String aggregateId,int limit);
 default String mode(){return "CUSTOM";}
}
