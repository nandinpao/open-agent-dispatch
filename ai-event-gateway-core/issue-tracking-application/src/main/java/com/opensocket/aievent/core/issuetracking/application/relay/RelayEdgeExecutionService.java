package com.opensocket.aievent.core.issuetracking.application.relay;

import com.opensocket.aievent.core.issuetracking.background.IssueBackgroundAuthorizationPort;
import com.opensocket.aievent.core.issuetracking.background.IssueBackgroundExecutionAuthorization;
import com.opensocket.aievent.core.issuetracking.relay.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelayEdgeExecutionService {
    private final RelayGovernanceRepository repository;
    private final RelayProviderGateway provider;
    private final RelayEventLedger ledger;
    private final RelayTopologyService topologies;
    private final Optional<IssueBackgroundAuthorizationPort> background;
    private final boolean backgroundRequired;

    public RelayEdgeExecutionService(RelayGovernanceRepository repository, RelayProviderGateway provider,
            RelayEventLedger ledger, RelayTopologyService topologies) {
        this(repository, provider, ledger, topologies, Optional.empty(), false);
    }

    @Autowired
    public RelayEdgeExecutionService(RelayGovernanceRepository repository, RelayProviderGateway provider,
            RelayEventLedger ledger, RelayTopologyService topologies,
            Optional<IssueBackgroundAuthorizationPort> background,
            @Value("${resource-access.background-enabled:false}") boolean backgroundRequired) {
        this.repository = repository; this.provider = provider; this.ledger = ledger; this.topologies = topologies;
        this.background = background; this.backgroundRequired = backgroundRequired;
    }

    @Transactional
    public RelayEdge execute(String tenantId,String edgeId,long expectedVersion,String actor,String reason,String correlationId) {
        RelayEdge current=repository.findEdge(tenantId,edgeId).orElseThrow(()->new IllegalArgumentException("Relay edge not found."));
        if(current.version()!=expectedVersion)throw new IllegalStateException("Relay edge version conflict.");
        if(current.status()==RelayEdgeStatus.SYNCED||current.status()==RelayEdgeStatus.COMPENSATED||current.status()==RelayEdgeStatus.SUPERSEDED)return current;
        IssueBackgroundExecutionAuthorization authorization=authorize(current,correlationId);
        try {
            OffsetDateTime now=now();
            RelayEdge processing=copy(current,RelayEdgeStatus.IN_PROGRESS,current.attemptCount()+1,null,current.providerObjectId(),"","",current.version()+1,now);
            if(!repository.saveEdgeExpectedVersion(processing,current.version()))throw new IllegalStateException("Relay edge version conflict.");
            ledger.append(tenantId,"RELAY_EDGE",edgeId,RelayEventType.EDGE_EXECUTION_STARTED,actor,reason,"{}",correlationId);
            authorization=checkpoint(authorization,"BEFORE_SIDE_EFFECT",correlationId);
            RelayProviderResult result=provider.execute(new RelayProviderCommand(tenantId,edgeId,current.topologyId(),current.connectionId(),current.projectMappingId(),current.providerType(),current.externalProjectId(),current.sourceExternalIssueId(),current.targetExternalIssueId(),current.edgeType(),current.relationType(),current.sourceMarker(),"/api/integrations/relay-topologies/"+current.topologyId(),message(current),"relay:"+edgeId+":"+processing.attemptCount(),correlationId));
            authorization=checkpoint(authorization,"BEFORE_RESULT_COMMIT",correlationId);
            RelayEdgeStatus status=result.success()?(current.edgeType()==RelayEdgeType.COMPENSATION_COMMENT||current.edgeType()==RelayEdgeType.COMPENSATION_TRANSITION?RelayEdgeStatus.COMPENSATED:RelayEdgeStatus.SYNCED):result.retryable()?RelayEdgeStatus.RETRY_WAITING:RelayEdgeStatus.FAILED_PERMANENT;
            OffsetDateTime nextAttempt=result.success()||!result.retryable()?null:now.plusSeconds(Math.min(3600,30L*processing.attemptCount()));
            RelayEdge saved=copy(processing,status,processing.attemptCount(),nextAttempt,result.providerObjectId(),result.reasonCode(),result.safeMessage(),processing.version()+1,now());
            if(!repository.saveEdgeExpectedVersion(saved,processing.version()))throw new IllegalStateException("Relay edge completion version conflict.");
            RelayEventType event=result.success()?RelayEventType.EDGE_SYNCED:result.retryable()?RelayEventType.EDGE_RETRY_SCHEDULED:RelayEventType.EDGE_FAILED_PERMANENT;
            ledger.append(tenantId,"RELAY_EDGE",edgeId,event,actor,result.reasonCode(),"{}",correlationId);
            topologies.recalculate(tenantId,current.topologyId(),actor,correlationId);
            complete(authorization,correlationId);
            return saved;
        } catch (RuntimeException ex) {
            revoke(authorization,"ISSUE_RELAY_EXECUTION_ABORTED",correlationId);
            throw ex;
        }
    }

    @Transactional
    public RelayEdge retry(String tenantId,String edgeId,long expectedVersion,String actor,String reason,String correlationId){
        RelayEdge current=repository.findEdge(tenantId,edgeId).orElseThrow(()->new IllegalArgumentException("Relay edge not found."));
        if(current.version()!=expectedVersion)throw new IllegalStateException("Relay edge version conflict.");
        if(current.status()!=RelayEdgeStatus.RETRY_WAITING&&current.status()!=RelayEdgeStatus.DEAD_LETTER&&current.status()!=RelayEdgeStatus.FAILED_PERMANENT)throw new IllegalStateException("Relay edge is not retryable.");
        RelayEdge ready=copy(current,RelayEdgeStatus.READY,current.attemptCount(),now(),current.providerObjectId(),"","",current.version()+1,now());
        if(!repository.saveEdgeExpectedVersion(ready,current.version()))throw new IllegalStateException("Relay edge version conflict.");
        return execute(tenantId,edgeId,ready.version(),actor,reason,correlationId);
    }

    public List<RelayEdge> due(String tenantId,OffsetDateTime at,int limit){return repository.claimDueEdges(tenantId,at,Math.max(1,Math.min(limit,500)));}

    private IssueBackgroundExecutionAuthorization authorize(RelayEdge edge,String correlation){
        String type=edge.projectMappingId().isBlank()?"ISSUE_CONNECTION":"ISSUE_PROJECT_MAPPING";
        String id=edge.projectMappingId().isBlank()?edge.connectionId():edge.projectMappingId();
        if(background.isPresent())return background.get().authorize(edge.tenantId(),"ISSUE_RELAY_EDGE",type,id,"integration.issue.worker.execute","Execute one governed Issue Relay edge",required(correlation));
        if(backgroundRequired)throw new IllegalStateException("BACKGROUND_RESOURCE_GUARD_UNAVAILABLE");
        return null;
    }
    private IssueBackgroundExecutionAuthorization checkpoint(IssueBackgroundExecutionAuthorization a,String phase,String correlation){return a==null?null:background.orElseThrow().checkpoint(a,phase,required(correlation));}
    private void complete(IssueBackgroundExecutionAuthorization a,String correlation){if(a!=null)background.orElseThrow().complete(a,required(correlation));}
    private void revoke(IssueBackgroundExecutionAuthorization a,String reason,String correlation){if(a!=null)background.ifPresent(b->b.revoke(a,reason,required(correlation)));}
    private String message(RelayEdge e){return switch(e.edgeType()){case SOURCE_BACKLINK->"OpenDispatch source backlink for relay topology "+e.topologyId();case TARGET_BACKLINK->"OpenDispatch target backlink for relay topology "+e.topologyId();case PROVIDER_NATIVE_RELATION->"Create provider-native relation for OpenDispatch relay topology "+e.topologyId();case COMPENSATION_COMMENT->"OpenDispatch compensation requested for relay topology "+e.topologyId();case COMPENSATION_TRANSITION->"Controlled compensation transition requested for relay topology "+e.topologyId();};}
    private RelayEdge copy(RelayEdge e,RelayEdgeStatus status,int attempt,OffsetDateTime next,String providerId,String code,String msg,long version,OffsetDateTime updated){return new RelayEdge(e.tenantId(),e.edgeId(),e.topologyId(),e.connectionId(),e.projectMappingId(),e.providerType(),e.externalProjectId(),e.sourceExternalIssueId(),e.targetExternalIssueId(),e.edgeType(),e.relationType(),e.sourceMarker(),status,attempt,next,providerId==null?e.providerObjectId():providerId,code,msg,version,e.createdAt(),updated,e.correlationId());}
    private static String required(String v){return v==null||v.isBlank()?"background-worker":v.trim();}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
}
