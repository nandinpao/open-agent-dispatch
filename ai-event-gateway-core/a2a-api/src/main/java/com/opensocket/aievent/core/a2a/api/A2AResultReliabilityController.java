package com.opensocket.aievent.core.a2a.api;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import com.opensocket.aievent.core.a2a.A2AResultProcessing;
import com.opensocket.aievent.core.a2a.A2AResultReliabilitySnapshot;
import com.opensocket.aievent.core.a2a.application.port.in.A2AResultReliabilityUseCase;
import com.opensocket.aievent.core.resourceaccess.contract.OperationPhase;
import com.opensocket.aievent.core.resourceaccess.contract.RequestChannel;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceEnforcementCommand;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.SecurityEpoch;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;

/** Read and governed repair API for Result Acceptance and parent aggregation reliability. */
@RestController
@RequestMapping("/api")
public class A2AResultReliabilityController {
    private final A2AApiRequestContextAccessor context;
    private final A2AResultReliabilityUseCase reliability;
    @Autowired(required = false)
    private ResourceAccessEnforcementPort resourceAccess;
    @Value("${resource-access.a2a-enabled:false}")
    private boolean a2aResourceAccessEnabled;

    public A2AResultReliabilityController(
            A2AApiRequestContextAccessor context,
            A2AResultReliabilityUseCase reliability) {
        this.context = context;
        this.reliability = reliability;
    }

    @GetMapping("/tasks/{taskId}/a2a-result-reliability")
    public A2AResultReliabilitySnapshot reliability(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "100") int limit) {
        authorize(ResourceType.TASK, taskId, "a2a.result.read", ResourceAction.ActionKind.READ,
                false, VisibilityLevel.SENSITIVE, "A2A_RESULT_RELIABILITY_READ");
        return reliability.reliability(tenant(), taskId, limit);
    }

    @PostMapping("/a2a-results/{resultId}/reconcile")
    public A2AResultProcessing reconcile(
            @PathVariable String resultId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReconcileRequest body) {
        authorize(ResourceType.TASK_RESULT, resultId, "a2a.result.reconcile", ResourceAction.ActionKind.UPDATE,
                true, VisibilityLevel.SENSITIVE, "A2A_RESULT_RECONCILE");
        try {
            return reliability.reconcile(
                    tenant(),
                    resultId,
                    actor(),
                    body == null ? null : body.reason(),
                    idempotencyKey);
        } catch (IllegalArgumentException exception) {
            throw bad(exception.getMessage());
        }
    }

    private void authorize(ResourceType type, String id, String permission, ResourceAction.ActionKind kind,
            boolean sideEffecting, VisibilityLevel visibility, String purpose) {
        if (!a2aResourceAccessEnabled) return;
        if (resourceAccess == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Resource Access enforcement guard is unavailable.");
        }
        resourceAccess.authorize(new ResourceEnforcementCommand(new ResourceAction(permission, kind, sideEffecting),
                new ResourceRef(tenant(), type, id), visibility, RequestChannel.REST, purpose,
                OperationPhase.START, SecurityEpoch.ZERO, Map.of()));
    }

    private String tenant() {
        A2AApiRequestContext current = context.current();
        if (current == null || current.tenantId() == null || current.tenantId().isBlank()) {
            throw bad("tenantId is required.");
        }
        return current.tenantId().trim();
    }

    private String actor() {
        A2AApiRequestContext current = context.current();
        return current == null || current.actorId() == null || current.actorId().isBlank()
                ? "unknown-operator"
                : current.actorId().trim();
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ReconcileRequest(String reason) {}
}
