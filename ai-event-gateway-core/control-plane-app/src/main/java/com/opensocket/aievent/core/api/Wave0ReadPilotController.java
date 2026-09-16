package com.opensocket.aievent.core.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotGateService;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotReadFacade;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotRepository;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotService;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0AdminSummaryView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0PermissionCatalogView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotObservation;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotOverview;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotResponse;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadinessEvidenceView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0RuntimeStatusView;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;

@RestController
@RequestMapping("/api/platform/enforcement-activation/wave0")
@ConditionalOnProperty(prefix = "aeg.enforcement-activation", name = "control-plane-enabled", havingValue = "true")
public class Wave0ReadPilotController {
    private final Wave0ReadPilotReadFacade reads;
    private final Wave0ReadPilotGateService gates;
    private final Wave0ReadPilotRepository repository;
    private final IamPermissionGuard guard;
    private final IamApiRequestContextFactory contexts;

    public Wave0ReadPilotController(
            Wave0ReadPilotReadFacade reads,
            Wave0ReadPilotGateService gates,
            Wave0ReadPilotRepository repository,
            IamPermissionGuard guard,
            IamApiRequestContextFactory contexts) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.gates = Objects.requireNonNull(gates, "gates");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @GetMapping
    public List<Wave0ReadPilotOverview> overview(HttpServletRequest request) {
        readContext(request, "OVERVIEW");
        return gates.overview();
    }

    @GetMapping("/observations")
    public List<Wave0ReadPilotObservation> observations(
            @RequestParam Wave0ReadPilotEntryPoint entryPoint,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request) {
        readContext(request, entryPoint.name());
        return repository.observations(entryPoint, limit);
    }

    @PostMapping("/entry-points/{entryPoint}/pause")
    public Wave0ReadPilotGateStatus pause(
            @PathVariable Wave0ReadPilotEntryPoint entryPoint,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = operationContext(request, entryPoint.name());
        return gates.pause(entryPoint, context.requireExpectedVersion(ifMatch), context.auditReason(),
                context.actorId(), context.correlationId());
    }

    @PostMapping("/entry-points/{entryPoint}/resume")
    public Wave0ReadPilotGateStatus resume(
            @PathVariable Wave0ReadPilotEntryPoint entryPoint,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = operationContext(request, entryPoint.name());
        return gates.resume(entryPoint, context.requireExpectedVersion(ifMatch), context.auditReason(),
                context.actorId(), context.correlationId());
    }

    @PostMapping("/entry-points/{entryPoint}/evaluate")
    public Wave0ReadPilotGateStatus evaluate(
            @PathVariable Wave0ReadPilotEntryPoint entryPoint,
            HttpServletRequest request) {
        IamApiRequestContext context = operationContext(request, entryPoint.name());
        return gates.evaluate(entryPoint, context.actorId(), context.correlationId());
    }

    @GetMapping("/read/runtime-status")
    public Wave0ReadPilotResponse<Wave0RuntimeStatusView> runtimeStatus(HttpServletRequest request) {
        return reads.runtimeStatus(actor(readContext(request, "RUNTIME_STATUS")));
    }

    @GetMapping("/read/readiness-evidence/{type}/{evidenceId}")
    public Wave0ReadPilotResponse<Wave0ReadinessEvidenceView> readinessEvidence(
            @PathVariable ReadinessEvidenceType type,
            @PathVariable UUID evidenceId,
            HttpServletRequest request) {
        return reads.readinessEvidence(type, evidenceId, actor(readContext(request, "READINESS_EVIDENCE")));
    }

    @GetMapping("/read/permission-catalog")
    public Wave0ReadPilotResponse<Wave0PermissionCatalogView> permissionCatalog(HttpServletRequest request) {
        return reads.activePermissionCatalog(actor(readContext(request, "PERMISSION_CATALOG")));
    }

    @GetMapping("/read/admin-summary")
    public Wave0ReadPilotResponse<Wave0AdminSummaryView> adminSummary(HttpServletRequest request) {
        return reads.nonSensitiveAdminSummary(actor(readContext(request, "ADMIN_SUMMARY")));
    }

    private IamApiRequestContext readContext(HttpServletRequest request, String resourceId) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(context, IamPermissions.ENFORCEMENT_WAVE0_READ,
                "ENFORCEMENT_WAVE0_READ_PILOT", resourceId);
        return context;
    }

    private IamApiRequestContext operationContext(HttpServletRequest request, String resourceId) {
        IamApiRequestContext context = contexts.from(request);
        context.requireAuditReason();
        context.requireIdempotencyKey();
        guard.requireInstance(context, IamPermissions.ENFORCEMENT_WAVE0_OPERATE,
                "ENFORCEMENT_WAVE0_READ_GATE", resourceId);
        return context;
    }

    private static Wave0ReadPilotService.ActorContext actor(IamApiRequestContext context) {
        return new Wave0ReadPilotService.ActorContext(
                "INSTANCE", context.actorId(), context.actorId(), context.correlationId());
    }
}
