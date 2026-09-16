package com.opensocket.aievent.core.a2a.application.service;

import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.A2ARequestCommand;
import com.opensocket.aievent.core.a2a.A2AResult;
import com.opensocket.aievent.core.a2a.A2AResultSubmission;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACancellationUseCase;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACompatibilityFacade;
import com.opensocket.aievent.core.a2a.application.port.in.A2AGovernanceUseCase;
import com.opensocket.aievent.core.a2a.application.port.in.A2AResultAcceptanceUseCase;

/** Deprecated entrypoint bridge. It never writes repositories and delegates to canonical use cases. */
public final class A2ACompatibilityFacadeService implements A2ACompatibilityFacade {
    private final A2ALegacyWriteGuard guard;
    private final A2AGovernanceUseCase governance;
    private final A2ACancellationUseCase cancellation;
    private final A2AResultAcceptanceUseCase results;

    public A2ACompatibilityFacadeService(A2ALegacyWriteGuard guard, A2AGovernanceUseCase governance,
            A2ACancellationUseCase cancellation, A2AResultAcceptanceUseCase results) {
        this.guard = guard;
        this.governance = governance;
        this.cancellation = cancellation;
        this.results = results;
    }

    public A2ARequest request(A2ARequestCommand command) {
        guard.requireAllowed("LEGACY_REQUEST");
        return governance.request(command);
    }
    public A2ARequest approve(String tenantId,String requestId,String actorType,String actorId,String key) {
        guard.requireAllowed("LEGACY_APPROVE");
        return governance.approve(tenantId,requestId,actorType,actorId,key);
    }
    public A2ARequest reject(String tenantId,String requestId,String code,String reason,String actorType,String actorId,String key) {
        guard.requireAllowed("LEGACY_REJECT");
        return governance.reject(tenantId,requestId,code,reason,actorType,actorId,key);
    }
    public A2ARequest cancel(String tenantId,String requestId,String actorType,String actorId,String reason,String key) {
        guard.requireAllowed("LEGACY_CANCEL");
        return cancellation.request(tenantId,requestId,actorType,actorId,reason,key);
    }
    public A2AResult acceptResult(A2AResultSubmission submission) {
        guard.requireAllowed("LEGACY_RESULT");
        return results.accept(submission);
    }
}
