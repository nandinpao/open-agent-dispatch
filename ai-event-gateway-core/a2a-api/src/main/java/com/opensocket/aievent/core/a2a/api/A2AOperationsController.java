package com.opensocket.aievent.core.a2a.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.a2a.A2AOperationsDetail;
import com.opensocket.aievent.core.a2a.A2AOperationsPage;
import com.opensocket.aievent.core.a2a.A2AOperationsSearchQuery;
import com.opensocket.aievent.core.a2a.application.port.in.A2AOperationsWorkspaceUseCase;

/** Read-only operational projection. All mutations remain in canonical A2A authority controllers. */
@RestController
@RequestMapping("/api/a2a-operations")
public class A2AOperationsController {
    private final A2AOperationsWorkspaceUseCase workspace;
    private final A2AApiRequestContextAccessor contextAccessor;

    public A2AOperationsController(A2AOperationsWorkspaceUseCase workspace,
            A2AApiRequestContextAccessor contextAccessor) {
        this.workspace = workspace;
        this.contextAccessor = contextAccessor;
    }

    @GetMapping
    public A2AOperationsPage search(
            @RequestParam(required = false) String requestStatus,
            @RequestParam(required = false) String blockerCode,
            @RequestParam(required = false) String operationalStage,
            @RequestParam(required = false) String sourceDomainId,
            @RequestParam(required = false) String targetDomainId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean blockerOnly,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return execute(() -> workspace.searchPage(new A2AOperationsSearchQuery(
                tenantId(), requestStatus, blockerCode, operationalStage, sourceDomainId,
                targetDomainId, q, blockerOnly, offset, limit, sortBy, sortDirection)));
    }

    @GetMapping("/{requestId}")
    public A2AOperationsDetail detail(@PathVariable String requestId) {
        return execute(() -> workspace.detail(tenantId(), requestId));
    }

    private String tenantId() {
        A2AApiRequestContext context = contextAccessor.current();
        if (context == null || context.tenantId() == null || context.tenantId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required.");
        }
        return context.tenantId().trim();
    }

    private <T> T execute(Operation<T> operation) {
        try {
            return operation.run();
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("not found")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface Operation<T> { T run(); }
}
