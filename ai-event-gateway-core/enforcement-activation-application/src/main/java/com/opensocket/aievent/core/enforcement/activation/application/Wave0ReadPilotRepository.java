package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Instant;
import java.util.List;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateState;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotMetricSummary;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotObservation;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotPolicy;

public interface Wave0ReadPilotRepository {
    Wave0ReadPilotPolicy policy(Wave0ReadPilotEntryPoint entryPoint);
    List<Wave0ReadPilotPolicy> policies();
    Wave0ReadPilotGateStatus gate(Wave0ReadPilotEntryPoint entryPoint);
    List<Wave0ReadPilotGateStatus> gates();
    void saveObservation(Wave0ReadPilotObservation observation);
    List<Wave0ReadPilotObservation> observations(Wave0ReadPilotEntryPoint entryPoint, int limit);
    Wave0ReadPilotMetricSummary metrics(Wave0ReadPilotEntryPoint entryPoint, Instant windowStartedAt, Instant evaluatedAt);
    Wave0ReadPilotGateStatus transition(
            Wave0ReadPilotEntryPoint entryPoint,
            long expectedVersion,
            Wave0ReadPilotGateState state,
            String reasonCode,
            String actorId,
            String correlationId,
            Instant changedAt);
}
