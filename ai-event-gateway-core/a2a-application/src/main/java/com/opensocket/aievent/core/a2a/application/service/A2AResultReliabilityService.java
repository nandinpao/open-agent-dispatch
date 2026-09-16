package com.opensocket.aievent.core.a2a.application.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import com.opensocket.aievent.core.a2a.A2AAggregationEvidenceRepository;
import com.opensocket.aievent.core.a2a.A2AParentAggregationRepository;
import com.opensocket.aievent.core.a2a.A2AResult;
import com.opensocket.aievent.core.a2a.A2AResultAttempt;
import com.opensocket.aievent.core.a2a.A2AResultAttemptRepository;
import com.opensocket.aievent.core.a2a.A2AResultEvidence;
import com.opensocket.aievent.core.a2a.A2AResultEvidenceRepository;
import com.opensocket.aievent.core.a2a.A2AResultProcessing;
import com.opensocket.aievent.core.a2a.A2AResultProcessingRepository;
import com.opensocket.aievent.core.a2a.A2AResultQuarantineRepository;
import com.opensocket.aievent.core.a2a.A2AResultReliabilitySnapshot;
import com.opensocket.aievent.core.a2a.A2AResultRepository;
import com.opensocket.aievent.core.a2a.application.port.in.A2AResultReliabilityUseCase;

/** Canonical application service for operational Result reliability queries and repair. */
public class A2AResultReliabilityService implements A2AResultReliabilityUseCase {
    private final A2AResultProcessingRepository processing;
    private final A2AResultRepository results;
    private final A2AResultAttemptRepository attempts;
    private final A2AResultEvidenceRepository evidence;
    private final A2AResultQuarantineRepository quarantine;
    private final A2AParentAggregationRepository aggregations;
    private final A2AAggregationEvidenceRepository aggregationEvidence;
    private final A2AResultCompletionCoordinator coordinator;

    public A2AResultReliabilityService(
            A2AResultProcessingRepository processing,
            A2AResultRepository results,
            A2AResultAttemptRepository attempts,
            A2AResultEvidenceRepository evidence,
            A2AResultQuarantineRepository quarantine,
            A2AParentAggregationRepository aggregations,
            A2AAggregationEvidenceRepository aggregationEvidence,
            A2AResultCompletionCoordinator coordinator) {
        this.processing = processing;
        this.results = results;
        this.attempts = attempts;
        this.evidence = evidence;
        this.quarantine = quarantine;
        this.aggregations = aggregations;
        this.aggregationEvidence = aggregationEvidence;
        this.coordinator = coordinator;
    }

    @Override
    public A2AResultReliabilitySnapshot reliability(String tenantId, String taskId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 500));
        List<A2AResultProcessing> states = processing.findByTask(tenantId, taskId, limit);
        List<A2AResultReliabilitySnapshot.ResultView> views = new ArrayList<>();
        LinkedHashSet<String> parents = new LinkedHashSet<>();
        for (A2AResultProcessing state : states) {
            A2AResult result = results.findById(tenantId, state.getResultId()).orElse(null);
            if (result == null) {
                continue;
            }
            parents.add(result.getParentTaskId());
            List<A2AResultAttempt> resultAttempts = attempts.findByRequest(tenantId, result.getRequestId(), limit);
            List<A2AResultEvidence> resultEvidence = resultAttempts.stream()
                    .flatMap(attempt -> evidence.findByAttempt(tenantId, attempt.getAttemptId(), limit).stream())
                    .toList();
            views.add(new A2AResultReliabilitySnapshot.ResultView(
                    result,
                    state,
                    resultAttempts,
                    resultEvidence,
                    quarantine.findByRequest(tenantId, result.getRequestId(), limit)));
        }
        List<A2AResultReliabilitySnapshot.AggregationView> aggregationViews = parents.stream()
                .map(parent -> new A2AResultReliabilitySnapshot.AggregationView(
                        parent,
                        aggregations.findByParentTask(tenantId, parent).orElse(null),
                        aggregationEvidence.findByParentTask(tenantId, parent, limit)))
                .toList();
        return new A2AResultReliabilitySnapshot(taskId, views, aggregationViews);
    }

    @Override
    public A2AResultProcessing reconcile(
            String tenantId,
            String resultId,
            String actorId,
            String reason,
            String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("RECONCILIATION_REASON_REQUIRED");
        }
        String actor = actorId == null || actorId.isBlank() ? "unknown-operator" : actorId.trim();
        return coordinator.process(tenantId, resultId, actor + ":" + reason.trim() + ":" + idempotencyKey.trim());
    }
}
