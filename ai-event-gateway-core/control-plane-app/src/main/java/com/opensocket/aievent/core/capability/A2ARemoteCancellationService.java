package com.opensocket.aievent.core.capability;

import java.util.Map;
import org.springframework.stereotype.Service;

/** C0-C1 durable remote cancellation; CANCEL response must pass current lease/epoch/stream fencing. */
@Service
public class A2ARemoteCancellationService {
    private final A2ARemoteTaskTrackingService tracking;
    private final A2ARemoteInterfaceRuntimeService runtime;
    private final A2AHttpJsonClient client;
    private final A2ARemoteTaskObservationService observation;
    private final A2APeerErrorMappingService errors;
    private final A2AExternalF0SecurityService security;
    private final A2ARemoteAuthorityService authority;

    public A2ARemoteCancellationService(
            A2ARemoteTaskTrackingService tracking,
            A2ARemoteInterfaceRuntimeService runtime,
            A2AHttpJsonClient client,
            A2ARemoteTaskObservationService observation,
            A2APeerErrorMappingService errors,
            A2AExternalF0SecurityService security,
            A2ARemoteAuthorityService authority) {
        this.tracking = tracking;
        this.runtime = runtime;
        this.client = client;
        this.observation = observation;
        this.errors = errors;
        this.security = security;
        this.authority = authority;
    }

    public void request(String tenant, String executionId) {
        tracking.requestCancel(tenant, executionId);
    }

    public void perform(String tenant, A2ARemoteTaskTrackingService.RemoteTrackingLease lease) {
        A2ARemoteInterfaceRuntimeService.ExecutionRuntime execution = runtime.execution(tenant, lease.executionId());
        if (execution == null) {
            tracking.retry(tenant, lease, "A2A_CANCEL_EXECUTION_UNAVAILABLE", lease.failureCount() + 1);
            return;
        }
        try {
            A2AExternalF0SecurityService.RuntimeSecurity runtimeSecurity = security.runtimeSecurityForExecution(tenant, lease.executionId());
            A2AHttpJsonClient.Response response = client.cancelTask(
                    execution.endpointUrl(),
                    execution.interfaceTenant(),
                    execution.protocolVersion(),
                    lease.remoteTaskId(),
                    runtimeSecurity);
            if (!response.ok()) {
                A2APeerErrorMappingService.Resolution resolution = errors.resolveAndRecord(
                        tenant,
                        lease.executionId(),
                        lease.trackingId(),
                        lease.peerId(),
                        lease.interfaceId(),
                        "CANCEL",
                        response.statusCode(),
                        response.body(),
                        response.rawBody());
                if (resolution.retryable()) {
                    tracking.mappedRetry(tenant, lease, resolution);
                } else {
                    tracking.mappedFailure(tenant, lease, resolution);
                }
                return;
            }
            A2ARemoteTaskObservationService.ObservationResult result = observation.observe(
                    tenant,
                    lease,
                    "CANCEL",
                    response.body(),
                    null,
                    authority.context(lease, "CANCEL"));
            Map<String, Object> task = A2AProtocolObjects.task(response.body());
            String state = A2AProtocolObjects.state(task);
            if (result.authoritative() && A2AProtocolObjects.terminal(state)) {
                tracking.cancelCompleted(tenant, lease.executionId());
            }
        } catch (Exception ex) {
            tracking.retry(tenant, lease, "A2A_CANCEL_FAILED:" + safe(ex.getMessage()), lease.failureCount() + 1);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
