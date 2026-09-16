package com.opensocket.aievent.core.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.opensocket.aievent.core.enforcement.activation.application.ClusterSnapshotStatusService;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlan;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlanService;
import com.opensocket.aievent.core.enforcement.activation.application.PublishCutoverResult;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteDefinition;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteKey;
import com.opensocket.aievent.core.enforcement.activation.contract.ClusterSnapshotStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanRoute;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshStatus;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;

@RestController
@RequestMapping("/api/platform/enforcement-activation")
@ConditionalOnProperty(
        prefix = "aeg.enforcement-activation",
        name = "control-plane-enabled",
        havingValue = "true")
public class EnforcementActivationController {
    private final CutoverPlanService service;
    private final ClusterSnapshotStatusService clusterStatus;
    private final IamPermissionGuard guard;
    private final IamApiRequestContextFactory contexts;

    public EnforcementActivationController(
            CutoverPlanService service,
            ClusterSnapshotStatusService clusterStatus,
            IamPermissionGuard guard,
            IamApiRequestContextFactory contexts) {
        this.service = Objects.requireNonNull(service, "service");
        this.clusterStatus = Objects.requireNonNull(clusterStatus, "clusterStatus");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @GetMapping("/plans")
    public List<CutoverPlan> plans(
            @RequestParam(required = false) CutoverPlanStatus status,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(
                context,
                IamPermissions.ENFORCEMENT_CUTOVER_READ,
                "ENFORCEMENT_CUTOVER_PLAN",
                "LIST");
        return service.list(status, limit);
    }

    @GetMapping("/plans/{planId}")
    public CutoverPlan plan(@PathVariable UUID planId, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(
                context,
                IamPermissions.ENFORCEMENT_CUTOVER_READ,
                "ENFORCEMENT_CUTOVER_PLAN",
                planId.toString());
        return service.get(planId);
    }

    @PostMapping("/plans")
    public ResponseEntity<CutoverPlan> create(
            @RequestBody CreatePlanRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = mutation(
                request,
                IamPermissions.ENFORCEMENT_CUTOVER_PLAN,
                "CREATE");
        CutoverPlan plan = service.create(body.title(), body.description(), command(context));
        return ResponseEntity.status(201).body(plan);
    }

    @PutMapping("/plans/{planId}/routes")
    public CutoverPlan routes(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody ReplaceRoutesRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = mutation(
                request,
                IamPermissions.ENFORCEMENT_CUTOVER_PLAN,
                planId.toString());
        return service.replaceRoutes(
                planId,
                context.requireExpectedVersion(ifMatch),
                body.routes().stream().map(RouteRequest::toRoute).toList(),
                command(context));
    }

    @PostMapping("/plans/{planId}/evidence")
    public CutoverPlan evidence(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody BindEvidenceRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = mutation(
                request,
                IamPermissions.ENFORCEMENT_CUTOVER_PLAN,
                planId.toString());
        return service.bindEvidence(
                planId,
                context.requireExpectedVersion(ifMatch),
                body.evidenceType(),
                body.evidenceId(),
                command(context));
    }

    @PostMapping("/plans/{planId}/submit")
    public CutoverPlan submit(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = mutation(
                request,
                IamPermissions.ENFORCEMENT_CUTOVER_REVIEW,
                planId.toString());
        return service.submit(
                planId,
                context.requireExpectedVersion(ifMatch),
                command(context));
    }

    @PostMapping("/plans/{planId}/approve")
    public CutoverPlan approve(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = mutation(
                request,
                IamPermissions.ENFORCEMENT_CUTOVER_APPROVE,
                planId.toString());
        return service.approve(
                planId,
                context.requireExpectedVersion(ifMatch),
                command(context));
    }

    @PostMapping("/plans/{planId}/reject")
    public CutoverPlan reject(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody RejectPlanRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = mutation(
                request,
                IamPermissions.ENFORCEMENT_CUTOVER_APPROVE,
                planId.toString());
        return service.reject(
                planId,
                context.requireExpectedVersion(ifMatch),
                body.reason(),
                command(context));
    }

    @PostMapping("/plans/{planId}/publish")
    public PublishCutoverResult publish(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = mutation(
                request,
                IamPermissions.ENFORCEMENT_CUTOVER_PUBLISH,
                planId.toString());
        return service.publish(
                planId,
                context.requireExpectedVersion(ifMatch),
                command(context));
    }

    @GetMapping("/runtime")
    public SnapshotRefreshStatus runtime(HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(
                context,
                IamPermissions.ENFORCEMENT_CUTOVER_READ,
                "ENFORCEMENT_RUNTIME",
                "ACTIVE");
        return service.refreshService().current();
    }

    @GetMapping("/runtime/cluster")
    public ClusterSnapshotStatus clusterRuntime(HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(
                context,
                IamPermissions.ENFORCEMENT_CUTOVER_READ,
                "ENFORCEMENT_RUNTIME_CLUSTER",
                "ACTIVE");
        return clusterStatus.current();
    }

    private IamApiRequestContext mutation(
            HttpServletRequest request,
            String permission,
            String resourceId) {
        IamApiRequestContext context = contexts.from(request);
        context.requireAuditReason();
        context.requireIdempotencyKey();
        guard.requireInstance(
                context,
                permission,
                "ENFORCEMENT_CUTOVER_PLAN",
                resourceId);
        return context;
    }

    private static CutoverPlanService.ActorCommand command(IamApiRequestContext context) {
        return new CutoverPlanService.ActorCommand(
                context.actorId(),
                context.auditReason(),
                context.correlationId(),
                context.idempotencyKey());
    }

    public record CreatePlanRequest(String title, String description) {}

    public record ReplaceRoutesRequest(List<RouteRequest> routes) {
        public ReplaceRoutesRequest {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }
    }

    public record BindEvidenceRequest(
            ReadinessEvidenceType evidenceType,
            UUID evidenceId) {
        public BindEvidenceRequest {
            Objects.requireNonNull(evidenceType, "evidenceType");
            Objects.requireNonNull(evidenceId, "evidenceId");
        }
    }

    public record RejectPlanRequest(String reason) {}

    public record RouteRequest(
            int routeOrder,
            String tenantId,
            String domain,
            String unit,
            String riskLane,
            String entryPoint,
            AuthorityMode mode,
            int targetBasisPoints,
            Set<String> includeCohorts,
            Set<String> excludeCohorts,
            String reasonCode) {
        CutoverPlanRoute toRoute() {
            return new CutoverPlanRoute(
                    routeOrder,
                    new AuthorityRouteDefinition(
                            new AuthorityRouteKey(
                                    tenantId,
                                    domain,
                                    unit,
                                    riskLane,
                                    entryPoint),
                            mode,
                            targetBasisPoints,
                            includeCohorts,
                            excludeCohorts,
                            reasonCode));
        }
    }
}
