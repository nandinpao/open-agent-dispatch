package com.opensocket.aievent.core.issuetracking.application.relay;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.issuetracking.core.RelayCompensationStateMachine;
import com.opensocket.aievent.core.issuetracking.core.RelaySourceMarker;
import com.opensocket.aievent.core.issuetracking.relay.RelayCompensation;
import com.opensocket.aievent.core.issuetracking.relay.RelayCompensationStatus;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdge;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdgeStatus;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdgeType;
import com.opensocket.aievent.core.issuetracking.relay.RelayEventType;
import com.opensocket.aievent.core.issuetracking.relay.RelayGovernanceRepository;
import com.opensocket.aievent.core.issuetracking.relay.RelayTopology;

@Service
public class RelayCompensationService {
    private final RelayGovernanceRepository repository;
    private final RelayEventLedger ledger;
    private final RelayEdgeExecutionService executor;
    private final RelayTopologyService topologies;
    private final RelayCompensationStateMachine machine = new RelayCompensationStateMachine();

    public RelayCompensationService(RelayGovernanceRepository repository,
            RelayEventLedger ledger,
            RelayEdgeExecutionService executor,
            RelayTopologyService topologies) {
        this.repository = repository;
        this.ledger = ledger;
        this.executor = executor;
        this.topologies = topologies;
    }

    @Transactional
    public RelayCompensation request(String tenantId,
            String topologyId,
            String reasonCode,
            String actor,
            String correlationId) {
        RelayTopology topology = repository.findTopology(tenantId, topologyId)
                .orElseThrow(() -> new IllegalArgumentException("Relay topology not found."));
        List<RelayEdge> synchronizedEdges = repository.listEdges(tenantId, topologyId,
                RelayEdgeStatus.SYNCED, 1000);
        OffsetDateTime now = now();
        RelayCompensation compensation = new RelayCompensation(tenantId,
                "relay-compensation-" + UUID.randomUUID(), topologyId, topology.canonicalRelationId(),
                reasonCode, actor, RelayCompensationStatus.REQUESTED, synchronizedEdges.size(),
                0, 0, 1, now, now, correlationId);
        repository.saveCompensation(compensation);
        ledger.append(tenantId, "RELAY_COMPENSATION", compensation.compensationId(),
                RelayEventType.COMPENSATION_REQUESTED, actor, reasonCode,
                "{\"edgeCount\":" + synchronizedEdges.size() + "}", correlationId);

        for (RelayEdge source : synchronizedEdges) {
            String edgeId = "relay-edge-" + UUID.randomUUID();
            RelayEdge compensationEdge = new RelayEdge(tenantId, edgeId, topologyId,
                    source.connectionId(), source.projectMappingId(), source.providerType(),
                    source.externalProjectId(), source.sourceExternalIssueId(), source.targetExternalIssueId(),
                    RelayEdgeType.COMPENSATION_COMMENT, source.relationType(),
                    RelaySourceMarker.create(tenantId, topologyId, edgeId), RelayEdgeStatus.READY,
                    0, now, "", "", "", 1, now, now, correlationId);
            repository.saveEdge(compensationEdge);
            ledger.append(tenantId, "RELAY_COMPENSATION", compensation.compensationId(),
                    RelayEventType.COMPENSATION_EDGE_CREATED, actor, "COMPENSATION_EDGE_CREATED",
                    "{\"edgeId\":\"" + edgeId + "\"}", correlationId);
        }
        return compensation;
    }

    @Transactional
    public RelayCompensation execute(String tenantId,
            String compensationId,
            long expectedVersion,
            String actor,
            String reason,
            String correlationId) {
        RelayCompensation current = repository.findCompensation(tenantId, compensationId)
                .orElseThrow(() -> new IllegalArgumentException("Relay compensation not found."));
        if (current.version() != expectedVersion) {
            throw new IllegalStateException("Relay compensation version conflict.");
        }
        List<RelayEdge> edges = repository.listEdges(tenantId, current.topologyId(), null, 1000).stream()
                .filter(edge -> edge.edgeType() == RelayEdgeType.COMPENSATION_COMMENT
                        || edge.edgeType() == RelayEdgeType.COMPENSATION_TRANSITION)
                .toList();
        int compensated = 0;
        int failed = 0;
        boolean retryableFailure = false;
        for (RelayEdge edge : edges) {
            RelayEdge result = edge.status() == RelayEdgeStatus.COMPENSATED
                    ? edge
                    : executor.execute(tenantId, edge.edgeId(), edge.version(), actor, reason, correlationId);
            if (result.status() == RelayEdgeStatus.COMPENSATED) {
                compensated++;
            } else if (result.status() == RelayEdgeStatus.RETRY_WAITING) {
                failed++;
                retryableFailure = true;
            } else if (result.status() == RelayEdgeStatus.FAILED_PERMANENT
                    || result.status() == RelayEdgeStatus.DEAD_LETTER) {
                failed++;
            }
        }
        RelayCompensationStatus status = machine.afterExecution(edges.size(), compensated, failed,
                retryableFailure);
        RelayCompensation next = new RelayCompensation(current.tenantId(), current.compensationId(),
                current.topologyId(), current.canonicalRelationId(), current.reasonCode(), current.requestedBy(),
                status, edges.size(), compensated, failed, current.version() + 1, current.createdAt(), now(),
                correlationId);
        if (!repository.saveCompensationExpectedVersion(next, current.version())) {
            throw new IllegalStateException("Relay compensation version conflict.");
        }
        RelayEventType eventType = status == RelayCompensationStatus.COMPLETED
                ? RelayEventType.COMPENSATION_COMPLETED
                : status == RelayCompensationStatus.PARTIAL
                        ? RelayEventType.COMPENSATION_PARTIAL
                        : RelayEventType.COMPENSATION_FAILED;
        ledger.append(tenantId, "RELAY_COMPENSATION", compensationId, eventType, actor, status.name(),
                "{\"compensated\":" + compensated + ",\"failed\":" + failed + "}", correlationId);
        topologies.recalculate(tenantId, current.topologyId(), actor, correlationId);
        return next;
    }

    public List<RelayCompensation> list(String tenantId,
            String topologyId,
            RelayCompensationStatus status,
            int limit) {
        return repository.listCompensations(tenantId, topologyId, status,
                Math.max(1, Math.min(limit, 1000)));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
