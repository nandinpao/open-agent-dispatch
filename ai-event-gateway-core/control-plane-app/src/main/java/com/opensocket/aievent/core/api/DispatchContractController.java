package com.opensocket.aievent.core.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.contract.DispatchContractBootstrapRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractBootstrapResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractChainInspectionRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractChainInspectionResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessResponse;
import com.opensocket.aievent.core.agent.contract.DispatchSourceSystemOption;
import com.opensocket.aievent.core.agent.contract.DispatchContractTestTaskRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractTestTaskResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractTraceRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractTraceResponse;
import com.opensocket.aievent.core.contract.DispatchContractTestTaskService;
import com.opensocket.aievent.core.contract.DispatchContractTraceService;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.source.SourceSystemManagementService;
import com.opensocket.aievent.core.source.SourceSystemView;

@RestController
@RequestMapping("/admin/dispatch-contracts")
public class DispatchContractController {
    private final AgentAssignmentService assignmentService;
    private final DispatchContractTestTaskService testTaskService;
    private final DispatchContractTraceService traceService;
    private final SourceSystemManagementService sourceSystemManagementService;

    public DispatchContractController(AgentAssignmentService assignmentService,
                                      DispatchContractTestTaskService testTaskService,
                                      DispatchContractTraceService traceService,
                                      SourceSystemManagementService sourceSystemManagementService) {
        this.assignmentService = assignmentService;
        this.testTaskService = testTaskService;
        this.traceService = traceService;
        this.sourceSystemManagementService = sourceSystemManagementService;
    }

    @GetMapping("/source-systems")
    public List<DispatchSourceSystemOption> sourceSystems(@RequestParam(required = false) String tenantId,
                                                          @RequestParam(defaultValue = "500") int limit) {
        // Phase 6 compatibility endpoint: Source System options come only from the
        // source_systems master table. They are no longer derived from Task
        // Definitions, Dispatch Flow Agent Assignments, Capability catalogs, or source-specific
        // dispatch defaults.
        return sourceSystemManagementService.list(tenantId).stream()
                .limit(Math.max(1, limit))
                .map(this::toLegacyOption)
                .toList();
    }

    private DispatchSourceSystemOption toLegacyOption(SourceSystemView source) {
        DispatchSourceSystemOption option = new DispatchSourceSystemOption();
        option.setTenantId(source.getTenantId());
        option.setSourceSystem(source.getSourceSystemId());
        option.setDisplayName(source.getDisplayName());
        option.setActive("ACTIVE".equalsIgnoreCase(source.getStatus()));
        option.setMetadata(java.util.Map.of("source", "source_systems_master"));
        return option;
    }

    @PostMapping("/bootstrap")
    public DispatchContractBootstrapResponse bootstrap(@RequestBody(required = false) DispatchContractBootstrapRequest request) {
        try {
            return assignmentService.bootstrapDispatchContract(request == null ? new DispatchContractBootstrapRequest() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }


    @PostMapping("/inspect")
    public DispatchContractChainInspectionResponse inspect(@RequestBody(required = false) DispatchContractChainInspectionRequest request) {
        try {
            return assignmentService.inspectDispatchContractChain(request == null ? new DispatchContractChainInspectionRequest() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/readiness")
    public DispatchContractReadinessResponse readiness(@RequestBody(required = false) DispatchContractReadinessRequest request) {
        try {
            return assignmentService.dispatchContractReadiness(request == null ? new DispatchContractReadinessRequest() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }


    @PostMapping("/trace")
    public DispatchContractTraceResponse trace(@RequestBody(required = false) DispatchContractTraceRequest request) {
        try {
            return traceService.trace(request == null ? new DispatchContractTraceRequest() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/test-task")
    public DispatchContractTestTaskResponse createTestTask(@RequestBody(required = false) DispatchContractTestTaskRequest request) {
        try {
            DispatchContractTestTaskRequest command = request == null
                    ? new DispatchContractTestTaskRequest() : request;
            String authorizedTenant = OpenDispatchRequestContextHolder.current()
                    .map(value -> value.tenantId() == null ? "" : value.tenantId().trim())
                    .orElse("");
            if (authorizedTenant.isBlank()) {
                throw new IllegalArgumentException("AUTH_TENANT_CONTEXT_REQUIRED");
            }
            String requestedTenant = command.getTenantId() == null ? "" : command.getTenantId().trim();
            if (!requestedTenant.isBlank() && !authorizedTenant.equals(requestedTenant)) {
                throw new IllegalArgumentException("AUTH_TENANT_MISMATCH");
            }
            // Tenant provenance is server-resolved from the authenticated request context.
            command.setTenantId(authorizedTenant);
            return testTaskService.createTestTask(command);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }
}
