package com.opensocket.aievent.core.capability;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** C0-C1/C0-C2 single-authority remote lifecycle worker with fenced renewal and convergence. */
@Component
@ConditionalOnProperty(name = "opendispatch.a2a-async.enabled", havingValue = "true", matchIfMissing = true)
public class A2ARemoteTrackingWorker {
    private final JdbcTemplate tenantJdbc;
    private final A2ARemoteTaskTrackingService tracking;
    private final A2ARemoteInterfaceRuntimeService runtime;
    private final CapabilityRemoteExecutionSafetyService safety;
    private final A2AHttpJsonClient client;
    private final A2ARemoteTaskObservationService observation;
    private final A2ARemoteCancellationService cancellation;
    private final A2APushNotificationConfigurationService push;
    private final A2APeerErrorMappingService errors;
    private final ProviderNeutralExecutionCompletionRouter completion;
    private final A2AExternalF0SecurityService security;
    private final A2ARemoteAuthorityService authority;
    private final int batchSize;

    public A2ARemoteTrackingWorker(
            JdbcTemplate tenantJdbc,
            A2ARemoteTaskTrackingService tracking,
            A2ARemoteInterfaceRuntimeService runtime,
            CapabilityRemoteExecutionSafetyService safety,
            A2AHttpJsonClient client,
            A2ARemoteTaskObservationService observation,
            A2ARemoteCancellationService cancellation,
            A2APushNotificationConfigurationService push,
            A2APeerErrorMappingService errors,
            ProviderNeutralExecutionCompletionRouter completion,
            A2AExternalF0SecurityService security,
            A2ARemoteAuthorityService authority,
            @org.springframework.beans.factory.annotation.Value("${opendispatch.a2a-async.batch-size:20}") int batchSize) {
        this.tenantJdbc = tenantJdbc;
        this.tracking = tracking;
        this.runtime = runtime;
        this.safety = safety;
        this.client = client;
        this.observation = observation;
        this.cancellation = cancellation;
        this.push = push;
        this.errors = errors;
        this.completion = completion;
        this.security = security;
        this.authority = authority;
        this.batchSize = Math.max(1, Math.min(batchSize, 50));
    }

    @Scheduled(fixedDelayString = "${opendispatch.a2a-async.poll-ms:3000}", scheduler = "a2aRemoteOperationalScheduler")
    public void run() {
        for (String tenant : tenantJdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id", String.class)) {
            for (A2ARemoteTaskTrackingService.RemoteTrackingLease lease : tracking.claimDue(tenant, authority.instanceId(), batchSize)) {
                process(tenant, lease);
            }
        }
    }

    private void process(String tenant, A2ARemoteTaskTrackingService.RemoteTrackingLease claimed) {
        A2ARemoteTaskTrackingService.RemoteTrackingLease current = claimed;
        try {
            // C0-C2 heartbeat: a stale or already-expired claimant stops before any remote effect.
            A2ARemoteTaskTrackingService.RemoteTrackingLease renewed = tracking.renewLease(tenant, current);
            if (renewed == null) {
                return;
            }
            current = renewed;
            if ("CANCELING".equals(current.status())) {
                cancellation.perform(tenant, current);
                return;
            }
            A2ARemoteInterfaceRuntimeService.ExecutionRuntime execution = runtime.execution(tenant, current.executionId());
            if (execution == null) {
                tracking.retry(tenant, current, "A2A_EXECUTION_NOT_FOUND", current.failureCount() + 1);
                return;
            }
            CapabilityRemoteExecutionSafetyService.Decision safetyDecision = safety.evaluate(tenant, execution.assignmentId());
            if (!safetyDecision.allowed()) {
                tracking.requestCancel(tenant, current.executionId());
                return;
            }
            A2ARemoteInterfaceRuntimeService.InterfaceRuntime iface = runtime.find(tenant, current.interfaceId());
            if (iface == null) {
                tracking.retry(tenant, current, "A2A_INTERFACE_NOT_APPROVED", current.failureCount() + 1);
                return;
            }
            A2AExternalF0SecurityService.RuntimeSecurity runtimeSecurity = security.runtimeSecurityForExecution(tenant, current.executionId());

            if (("PUSH".equals(current.mode()) || "HYBRID".equals(current.mode()))
                    && current.pushConfigId() == null
                    && !tracking.pushBaseUrl().isBlank()) {
                push.configure(tenant, current);
            }

            // Pure PUSH mode is owner-independent in C0-C2: ingress is durable and the current
            // lease owner consumes it through A2APushHandoffWorker. Do not issue a non-authority
            // GET_TASK merely to keep the lease busy.
            if ("PUSH".equals(current.mode())) {
                return;
            }

            boolean streamSucceeded = false;
            if (("STREAM".equals(current.mode()) || "HYBRID".equals(current.mode())) && iface.streamingSupported()) {
                try {
                    A2AHttpJsonClient.StreamResponse stream = client.subscribe(
                            execution.endpointUrl(),
                            execution.interfaceTenant(),
                            execution.protocolVersion(),
                            current.remoteTaskId(),
                            runtimeSecurity);
                    if (stream.statusCode() >= 200 && stream.statusCode() < 300) {
                        streamSucceeded = true;
                        A2ARemoteTaskTrackingService.RemoteTrackingLease postSubscribe = tracking.renewLease(tenant, current);
                        if (postSubscribe == null) {
                            return;
                        }
                        current = postSubscribe;
                        List<Map<String, Object>> events = stream.events();
                        for (int i = 0; i < events.size(); i++) {
                            boolean releaseAfter = i == events.size() - 1;
                            A2ARemoteTaskObservationService.ObservationResult result = observation.observe(
                                    tenant,
                                    current,
                                    "STREAM",
                                    events.get(i),
                                    null,
                                    authority.context(current, "STREAM"),
                                    releaseAfter);
                            if (result.authoritative() && A2AProtocolObjects.terminal(A2AProtocolObjects.state(A2AProtocolObjects.task(events.get(i))))) {
                                return;
                            }
                        }
                    } else {
                        A2APeerErrorMappingService.Resolution resolution = errors.resolveAndRecord(
                                tenant,
                                current.executionId(),
                                current.trackingId(),
                                current.peerId(),
                                current.interfaceId(),
                                "SUBSCRIBE",
                                stream.statusCode(),
                                Map.of(),
                                "A2A subscribe HTTP " + stream.statusCode());
                        if (!resolution.retryable()) {
                            finishMappedFailure(tenant, current, execution, resolution);
                            return;
                        }
                    }
                } catch (Exception ignored) {
                    // Transport failure alone is not authority. Explicit POLL fallback below advances authority epoch.
                }
            }

            if (!streamSucceeded && ("STREAM".equals(current.mode()) || "HYBRID".equals(current.mode()))) {
                current = tracking.switchAuthority(tenant, current, "POLL");
            }

            A2AHttpJsonClient.Response response = client.getTask(
                    execution.endpointUrl(),
                    execution.interfaceTenant(),
                    execution.protocolVersion(),
                    current.remoteTaskId(),
                    runtimeSecurity);
            if (!response.ok()) {
                A2APeerErrorMappingService.Resolution resolution = errors.resolveAndRecord(
                        tenant,
                        current.executionId(),
                        current.trackingId(),
                        current.peerId(),
                        current.interfaceId(),
                        "GET_TASK",
                        response.statusCode(),
                        response.body(),
                        response.rawBody());
                if (resolution.retryable()) {
                    tracking.mappedRetry(tenant, current, resolution);
                } else {
                    finishMappedFailure(tenant, current, execution, resolution);
                }
                return;
            }

            // If STREAM remained authority, this POLL snapshot is reconciliation evidence only.
            // If switchAuthority(...,"POLL") advanced the epoch, this exact POLL stream is authoritative.
            A2ARemoteTaskObservationService.ObservationResult poll = observation.observe(
                    tenant,
                    current,
                    "POLL",
                    response.body(),
                    null,
                    authority.context(current, "POLL"));
            tracking.markReconciled(tenant, current);
            if (!poll.authoritative()) {
                tracking.releaseIfOwned(tenant, current);
            }
        } catch (Exception ex) {
            tracking.retry(tenant, current, "A2A_RECONCILIATION_FAILED:" + safe(ex.getMessage()), current.failureCount() + 1);
        }
    }

    private void finishMappedFailure(
            String tenant,
            A2ARemoteTaskTrackingService.RemoteTrackingLease lease,
            A2ARemoteInterfaceRuntimeService.ExecutionRuntime execution,
            A2APeerErrorMappingService.Resolution resolution) {
        if (!tracking.mappedFailure(tenant, lease, resolution)) {
            return;
        }
        completion.complete(
                tenant,
                execution.executionContextType(),
                execution.delegationId(),
                execution.planRunId(),
                execution.planStepId(),
                execution.taskId(),
                "REMOTE_A2A_AGENT",
                execution.providerId(),
                lease.executionId(),
                false,
                Map.of(
                        "remoteErrorCode", safe(resolution.remoteErrorCode()),
                        "errorClass", resolution.resolvedErrorClass(),
                        "disposition", resolution.disposition()),
                resolution.canonicalErrorCode(),
                safe(resolution.remoteErrorMessage()));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
