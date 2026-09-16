package com.opensocket.aievent.core.issuetracking.application.relay;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.issuetracking.core.RelaySourceMarker;
import com.opensocket.aievent.core.issuetracking.core.RelayTopologyAggregateCalculator;
import com.opensocket.aievent.core.issuetracking.relay.A2ARelayPolicySignalPort;
import com.opensocket.aievent.core.issuetracking.relay.CanonicalIssueRelation;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdge;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdgeDefinition;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdgeStatus;
import com.opensocket.aievent.core.issuetracking.relay.RelayEventType;
import com.opensocket.aievent.core.issuetracking.relay.RelayGovernanceRepository;
import com.opensocket.aievent.core.issuetracking.relay.RelayPolicySignal;
import com.opensocket.aievent.core.issuetracking.relay.RelayPolicySignalResult;
import com.opensocket.aievent.core.issuetracking.relay.RelayTopology;
import com.opensocket.aievent.core.issuetracking.relay.RelayTopologyStatus;
import com.opensocket.aievent.core.issuetracking.relay.RelayTopologyType;

@Service
public class RelayTopologyService {
    private final RelayGovernanceRepository repository;
    private final RelayEventLedger ledger;
    private final RelayTopologyAggregateCalculator calculator = new RelayTopologyAggregateCalculator();
    private final A2ARelayPolicySignalPort policyPort;

    public RelayTopologyService(RelayGovernanceRepository repository,
            RelayEventLedger ledger,
            A2ARelayPolicySignalPort policyPort) {
        this.repository = repository;
        this.ledger = ledger;
        this.policyPort = policyPort;
    }

    @Transactional
    public RelayTopology create(String tenantId,
            String canonicalRelationId,
            RelayTopologyType type,
            List<RelayEdgeDefinition> definitions,
            String actor,
            String correlationId) {
        CanonicalIssueRelation relation = repository.findRelation(tenantId, canonicalRelationId)
                .orElseThrow(() -> new IllegalArgumentException("Canonical relation not found."));
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("At least one relay edge is required.");
        }
        OffsetDateTime now = now();
        String topologyId = "relay-topology-" + UUID.randomUUID();
        RelayTopology topology = new RelayTopology(tenantId, topologyId, relation.relationId(), type,
                RelayTopologyStatus.PARTIAL, definitions.size(), 0, 0, 0, 1, now, now, correlationId);
        repository.saveTopology(topology);
        ledger.append(tenantId, "RELAY_TOPOLOGY", topologyId, RelayEventType.TOPOLOGY_CREATED,
                actor, "RELAY_TOPOLOGY_CREATED", "{\"edgeCount\":" + definitions.size() + "}", correlationId);

        for (RelayEdgeDefinition definition : definitions) {
            String edgeId = "relay-edge-" + UUID.randomUUID();
            RelayEdge edge = new RelayEdge(tenantId, edgeId, topologyId,
                    definition.connectionId(), definition.projectMappingId(), definition.providerType(),
                    definition.externalProjectId(), definition.sourceExternalIssueId(),
                    definition.targetExternalIssueId(), definition.edgeType(), definition.relationType(),
                    RelaySourceMarker.create(tenantId, topologyId, edgeId), RelayEdgeStatus.READY,
                    0, now, "", "", "", 1, now, now, correlationId);
            repository.saveEdge(edge);
            ledger.append(tenantId, "RELAY_EDGE", edgeId, RelayEventType.EDGE_CREATED,
                    actor, "RELAY_EDGE_CREATED",
                    "{\"edgeType\":\"" + definition.edgeType().name() + "\"}", correlationId);
        }
        return recalculate(tenantId, topologyId, actor, correlationId);
    }

    @Transactional
    public RelayTopology recalculate(String tenantId, String topologyId, String actor, String correlationId) {
        RelayTopology current = repository.findTopology(tenantId, topologyId)
                .orElseThrow(() -> new IllegalArgumentException("Relay topology not found."));
        List<RelayEdge> edges = repository.listEdges(tenantId, topologyId, null, 1000);
        RelayTopologyStatus status = calculator.calculate(edges);
        int synced = (int) edges.stream()
                .filter(edge -> edge.status() == RelayEdgeStatus.SYNCED
                        || edge.status() == RelayEdgeStatus.COMPENSATED)
                .count();
        int failed = (int) edges.stream()
                .filter(edge -> edge.status() == RelayEdgeStatus.FAILED_PERMANENT
                        || edge.status() == RelayEdgeStatus.DEAD_LETTER)
                .count();
        int decision = (int) edges.stream()
                .filter(edge -> edge.lastErrorCode().equals("CONTROLLED_DECISION_REQUIRED")
                        || edge.lastErrorCode().equals("PROVIDER_NATIVE_RELATION_UNSUPPORTED"))
                .count();
        RelayTopology next = new RelayTopology(current.tenantId(), current.topologyId(),
                current.canonicalRelationId(), current.topologyType(), status, edges.size(), synced, failed,
                decision, current.version() + 1, current.createdAt(), now(), correlationId);
        if (!repository.saveTopologyExpectedVersion(next, current.version())) {
            throw new IllegalStateException("Relay topology version conflict.");
        }
        String metadata = "{\"synced\":" + synced + ",\"failed\":" + failed
                + ",\"decision\":" + decision + "}";
        ledger.append(tenantId, "RELAY_TOPOLOGY", topologyId, RelayEventType.TOPOLOGY_RECALCULATED,
                actor, status.name(), metadata, correlationId);

        RelayPolicySignalResult signal = policyPort.publish(new RelayPolicySignal(tenantId, topologyId,
                current.canonicalRelationId(), status, synced, failed, decision, now(), correlationId));
        ledger.append(tenantId, "RELAY_TOPOLOGY", topologyId, RelayEventType.POLICY_SIGNAL_PUBLISHED,
                actor, signal.status().name(), "{\"evidence\":\"" + jsonSafe(signal.evidenceReference()) + "\"}",
                correlationId);
        return next;
    }

    public Optional<RelayTopology> find(String tenantId, String topologyId) {
        return repository.findTopology(tenantId, topologyId);
    }

    public List<RelayTopology> list(String tenantId, RelayTopologyStatus status, int limit) {
        return repository.listTopologies(tenantId, status, Math.max(1, Math.min(limit, 1000)));
    }

    public List<RelayEdge> edges(String tenantId, String topologyId, RelayEdgeStatus status, int limit) {
        return repository.listEdges(tenantId, topologyId, status, Math.max(1, Math.min(limit, 1000)));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String jsonSafe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "'");
    }
}
