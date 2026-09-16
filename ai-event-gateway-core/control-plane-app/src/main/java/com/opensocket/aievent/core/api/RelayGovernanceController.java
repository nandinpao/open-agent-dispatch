package com.opensocket.aievent.core.api;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.issuetracking.application.relay.CanonicalRelationService;
import com.opensocket.aievent.core.issuetracking.application.relay.RelayCompensationService;
import com.opensocket.aievent.core.issuetracking.application.relay.RelayEdgeExecutionService;
import com.opensocket.aievent.core.issuetracking.relay.RelayGovernanceRepository;
import com.opensocket.aievent.core.issuetracking.application.relay.RelayTopologyService;
import com.opensocket.aievent.core.issuetracking.relay.CanonicalIssueRelation;
import com.opensocket.aievent.core.issuetracking.relay.CanonicalRelationStatus;
import com.opensocket.aievent.core.issuetracking.relay.RelayCompensation;
import com.opensocket.aievent.core.issuetracking.relay.RelayCompensationStatus;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdge;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdgeDefinition;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdgeStatus;
import com.opensocket.aievent.core.issuetracking.relay.RelayEdgeType;
import com.opensocket.aievent.core.issuetracking.relay.RelayEvent;
import com.opensocket.aievent.core.issuetracking.relay.RelayTopology;
import com.opensocket.aievent.core.issuetracking.relay.RelayTopologyStatus;
import com.opensocket.aievent.core.issuetracking.relay.RelayTopologyType;

/**
 * Operator API for Phase 3H canonical relation, relay topology, independent edge and
 * compensation governance.
 */
@RestController
@RequestMapping("/api/integrations/relay-governance")
public class RelayGovernanceController {

    private final CanonicalRelationService relations;
    private final RelayTopologyService topologies;
    private final RelayEdgeExecutionService edges;
    private final RelayCompensationService compensations;
    private final RelayGovernanceRepository repository;

    public RelayGovernanceController(
            CanonicalRelationService relations,
            RelayTopologyService topologies,
            RelayEdgeExecutionService edges,
            RelayCompensationService compensations,
            RelayGovernanceRepository repository) {
        this.relations = relations;
        this.topologies = topologies;
        this.edges = edges;
        this.compensations = compensations;
        this.repository = repository;
    }

    @GetMapping("/canonical-relations")
    public List<CanonicalIssueRelation> relations(
            @RequestParam(required = false) CanonicalRelationStatus status,
            @RequestParam(defaultValue = "200") int limit) {
        return run(() -> relations.list(tenant(), status, limit));
    }

    @PostMapping("/canonical-relations")
    public CanonicalIssueRelation createRelation(@RequestBody CanonicalRelationRequest body) {
        throw legacyRelayRetired();
    }

    @GetMapping("/topologies")
    public List<RelayTopology> topologies(
            @RequestParam(required = false) RelayTopologyStatus status,
            @RequestParam(defaultValue = "200") int limit) {
        return run(() -> topologies.list(tenant(), status, limit));
    }

    @GetMapping("/topologies/{topologyId}")
    public RelayTopology topology(@PathVariable String topologyId) {
        return run(() -> topologies.find(tenant(), topologyId)
                .orElseThrow(() -> bad("Relay topology not found.")));
    }

    @GetMapping("/topologies/{topologyId}/edges")
    public List<RelayEdge> topologyEdges(
            @PathVariable String topologyId,
            @RequestParam(required = false) RelayEdgeStatus status,
            @RequestParam(defaultValue = "500") int limit) {
        return run(() -> topologies.edges(tenant(), topologyId, status, limit));
    }

    @GetMapping("/topologies/{topologyId}/events")
    public List<RelayEvent> topologyEvents(
            @PathVariable String topologyId,
            @RequestParam(defaultValue = "200") int limit) {
        return run(() -> repository.listEvents(
                tenant(),
                "RELAY_TOPOLOGY",
                topologyId,
                Math.max(1, Math.min(limit, 1000))));
    }

    @PostMapping("/topologies")
    public RelayTopology createTopology(@RequestBody RelayTopologyRequest body) {
        throw legacyRelayRetired();
    }

    @PostMapping("/edges/{edgeId}/execute")
    public RelayEdge execute(
            @PathVariable String edgeId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody GovernedReason body) {
        throw legacyRelayRetired();
    }

    @PostMapping("/edges/{edgeId}/retry")
    public RelayEdge retry(
            @PathVariable String edgeId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody GovernedReason body) {
        throw legacyRelayRetired();
    }

    @GetMapping("/compensations")
    public List<RelayCompensation> compensations(
            @RequestParam(required = false) String topologyId,
            @RequestParam(required = false) RelayCompensationStatus status,
            @RequestParam(defaultValue = "200") int limit) {
        return run(() -> compensations.list(tenant(), text(topologyId), status, limit));
    }

    @PostMapping("/topologies/{topologyId}/compensations")
    public RelayCompensation requestCompensation(
            @PathVariable String topologyId,
            @RequestBody CompensationRequest body) {
        throw legacyRelayRetired();
    }

    @PostMapping("/compensations/{compensationId}/execute")
    public RelayCompensation executeCompensation(
            @PathVariable String compensationId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody GovernedReason body) {
        throw legacyRelayRetired();
    }

    @GetMapping("/compensations/{compensationId}/events")
    public List<RelayEvent> compensationEvents(
            @PathVariable String compensationId,
            @RequestParam(defaultValue = "200") int limit) {
        return run(() -> repository.listEvents(
                tenant(),
                "RELAY_COMPENSATION",
                compensationId,
                Math.max(1, Math.min(limit, 1000))));
    }


    private ResponseStatusException legacyRelayRetired() {
        return new ResponseStatusException(HttpStatus.GONE,
                "LEGACY_ISSUE_RELAY_RETIRED_USE_A2A: Cross-project Issue relay mutation is retired. Use A2A for cross-domain collaboration; historical relay evidence remains read-only.");
    }

    private OpenDispatchRequestContext context() {
        return OpenDispatchRequestContextHolder.current()
                .orElseThrow(() -> bad("Request context is required."));
    }

    private String tenant() {
        return required(context().tenantId(), "tenantId");
    }

    private String operator() {
        return required(context().operatorId(), "operatorId");
    }

    private String correlation() {
        return text(context().correlationId());
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw bad(name + " is required.");
        }
        return value.trim();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private long version(String ifMatch) {
        try {
            String normalized = required(ifMatch, "If-Match")
                    .replace("W/", "")
                    .replace("\"", "")
                    .trim();
            long value = Long.parseLong(normalized);
            if (value < 1) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw bad("If-Match must contain a positive row version.");
        }
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private <T> T run(Operation<T> operation) {
        try {
            return operation.get();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw bad(exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T get();
    }

    public record CanonicalRelationRequest(
            String relationType,
            String parentTaskId,
            String childTaskId,
            String sourceTaskIssueLinkId,
            String targetTaskIssueLinkId) {
    }

    public record RelayEdgeRequest(
            String connectionId,
            String projectMappingId,
            String providerType,
            String externalProjectId,
            String sourceExternalIssueId,
            String targetExternalIssueId,
            RelayEdgeType edgeType,
            String relationType) {
    }

    public record RelayTopologyRequest(
            String canonicalRelationId,
            RelayTopologyType topologyType,
            List<RelayEdgeRequest> edges) {
    }

    public record GovernedReason(String reason) {
    }

    public record CompensationRequest(String reasonCode) {
    }
}
