package com.opensocket.aievent.core.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.integration.issue.projection.CrossProjectIssueRelay;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayAttempt;
import com.opensocket.aievent.core.integration.issue.IssueRelayService;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;

/** Issue Tracking projection API. It cannot mutate A2A, Task, Assignment or Result authority. */
@RestController
@RequestMapping
public class IssueRelayController {
    private final IssueRelayService service;
    @Autowired(required=false) private ScopedBusinessResourceAccessCoordinator scopedAccess;

    public IssueRelayController(IssueRelayService service) {
        this.service = service;
    }

    @GetMapping("/api/issue-relays/{relayId}")
    public CrossProjectIssueRelay relay(@PathVariable String relayId) {
        return run(() -> authorize(service.relay(tenant(), relayId)));
    }

    @GetMapping("/api/issue-relays")
    public List<CrossProjectIssueRelay> relays(
            @RequestParam(required = false) String taskId,
            @RequestParam(defaultValue = "200") int limit) {
        return run(() -> service.relays(tenant(), taskId, limit).stream().filter(this::canRead).toList());
    }

    @GetMapping("/api/issue-relays/{relayId}/attempts")
    public List<IssueRelayAttempt> attempts(
            @PathVariable String relayId,
            @RequestParam(defaultValue = "200") int limit) {
        return run(() -> { authorize(service.relay(tenant(), relayId)); return service.relayAttempts(tenant(), relayId, limit); });
    }


    private boolean canRead(CrossProjectIssueRelay relay){try{authorize(relay);return true;}catch(RuntimeException denied){return false;}}
    private CrossProjectIssueRelay authorize(CrossProjectIssueRelay relay){
        if(relay==null||scopedAccess==null)return relay;
        authorizeTask(relay.sourceTaskId(),"RS5_ISSUE_RELAY_SOURCE");
        authorizeTask(relay.targetTaskId(),"RS5_ISSUE_RELAY_TARGET");
        return relay;
    }
    private void authorizeTask(String taskId,String purpose){
        if(taskId==null||taskId.isBlank())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"RS5_EXTERNAL_SCOPE_EXPANSION_BLOCKED: relay Task scope is unresolved.");
        scopedAccess.authorize(ResourceType.TASK,taskId,"task.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,purpose);
    }

    private String tenant() {
        return required(context().tenantId(), "tenantId");
    }

    private OpenDispatchRequestContext context() {
        return OpenDispatchRequestContextHolder.current()
                .orElseThrow(() -> bad("Request context is required."));
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw bad(name + " is required.");
        }
        return value.trim();
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
            if (exception.getMessage() != null
                    && exception.getMessage().contains("not found in Tenant")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
            }
            throw bad(exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T get();
    }

}
