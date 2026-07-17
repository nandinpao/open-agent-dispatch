package com.opensocket.aievent.core.api;

import java.time.Duration;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterWorkItemMapper;
import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import com.opensocket.aievent.service.adapter.AdapterWorkerHeartbeatRequest;
import com.opensocket.aievent.service.adapter.AdapterWorkerCompletionRequest;
import com.opensocket.aievent.service.adapter.AdapterWorkerFailureRequest;
import com.opensocket.aievent.core.action.AdapterActionFacade;
import com.opensocket.aievent.core.action.AdapterType;

@RestController
@RequestMapping("/internal/adapter-actions")
public class InternalAdapterActionWorkerController {
    private final AdapterActionFacade service;

    public InternalAdapterActionWorkerController(AdapterActionFacade service) {
        this.service = service;
    }

    @PostMapping("/claim")
    public StandardApiResponse<AdapterWorkItem> claim(@RequestParam AdapterType adapterType,
                                                       @RequestParam String workerId,
                                                       @RequestParam(defaultValue = "120") long leaseSeconds) {
        return StandardApiResponse.ok(service.claimNext(adapterType, workerId, Duration.ofSeconds(Math.max(1, leaseSeconds)))
                .map(AdapterWorkItemMapper::from)
                .orElse(null));
    }

    @PostMapping("/{actionId}/heartbeat")
    public AdapterAction heartbeat(@PathVariable String actionId,
                                   @RequestBody(required = false) AdapterWorkerHeartbeatRequest body,
                                   @RequestParam(required = false) String workerId,
                                   @RequestParam(defaultValue = "120") long leaseSeconds) {
        String effectiveWorkerId = body != null && body.workerId() != null ? body.workerId() : workerId;
        long effectiveLeaseSeconds = body != null && body.leaseSeconds() != null ? body.leaseSeconds() : leaseSeconds;
        return service.heartbeat(actionId, effectiveWorkerId, Duration.ofSeconds(Math.max(1, effectiveLeaseSeconds)));
    }

    @PostMapping("/{actionId}/complete")
    public AdapterAction complete(@PathVariable String actionId,
                                  @RequestBody(required = false) AdapterWorkerCompletionRequest body) {
        return service.completeByWorker(actionId,
                body == null ? null : body.workerId(),
                body == null ? null : body.responseRef());
    }

    @PostMapping("/{actionId}/fail")
    public AdapterAction fail(@PathVariable String actionId,
                              @RequestBody(required = false) AdapterWorkerFailureRequest body) {
        return service.failByWorker(actionId,
                body == null ? null : body.workerId(),
                body == null ? null : body.error(),
                body == null ? null : body.retryable());
    }

    @PostMapping("/{actionId}/recover-expired-lease")
    public AdapterAction recoverExpiredLease(@PathVariable String actionId) {
        return service.recoverExpiredWorkerLease(actionId);
    }

    @PostMapping("/recover-expired-leases")
    public java.util.List<AdapterAction> recoverExpiredLeases(@RequestParam(defaultValue = "100") int limit) {
        return service.recoverExpiredWorkerLeases(limit);
    }

}
