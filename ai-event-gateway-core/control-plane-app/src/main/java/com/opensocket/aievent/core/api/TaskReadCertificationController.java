package com.opensocket.aievent.core.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.opensocket.aievent.core.enforcement.activation.application.TaskReadCertificationService;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.TaskReadCertificationEvidence;
import com.opensocket.aievent.core.enforcement.activation.contract.TaskReadCertificationStatus;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;

@RestController
@RequestMapping("/api/platform/enforcement-activation/wave0/task-read-certifications")
@ConditionalOnProperty(prefix = "aeg.enforcement-activation", name = "control-plane-enabled", havingValue = "true")
public final class TaskReadCertificationController {
    private final TaskReadCertificationService service;
    private final IamPermissionGuard guard;
    private final IamApiRequestContextFactory contexts;

    public TaskReadCertificationController(TaskReadCertificationService service, IamPermissionGuard guard,
            IamApiRequestContextFactory contexts) {
        this.service = Objects.requireNonNull(service, "service");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @GetMapping
    public List<TaskReadCertificationEvidence> recent(@RequestParam String tenantId,
            @RequestParam(defaultValue = "20") int limit, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(context, IamPermissions.ENFORCEMENT_WAVE0_READ,
                "ENFORCEMENT_TASK_READ_CERTIFICATION", tenantId);
        return service.recent(tenantId, limit);
    }

    @PostMapping
    public TaskReadCertificationEvidence certify(@RequestBody CertificationRequest body, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        context.requireAuditReason();
        String idempotencyKey = context.requireIdempotencyKey();
        guard.requireInstance(context, IamPermissions.ENFORCEMENT_WAVE0_OPERATE,
                "ENFORCEMENT_TASK_READ_CERTIFICATION", body.tenantId());
        return service.certify(new TaskReadCertificationService.Command(
                body.tenantId(), body.authorityRevision(), body.routeMode(), body.deterministicOrderStatus(),
                body.cursorPaginationStatus(), body.nPlusOneStatus(), body.forceRlsStatus(),
                body.sensitiveFieldMaskingStatus(), body.fallbackPauseStatus(), body.loadTestStatus(),
                body.evidenceJson(), context.actorId(), context.correlationId(), idempotencyKey));
    }

    public record CertificationRequest(String tenantId, long authorityRevision, AuthorityMode routeMode,
            TaskReadCertificationStatus deterministicOrderStatus,
            TaskReadCertificationStatus cursorPaginationStatus,
            TaskReadCertificationStatus nPlusOneStatus,
            TaskReadCertificationStatus forceRlsStatus,
            TaskReadCertificationStatus sensitiveFieldMaskingStatus,
            TaskReadCertificationStatus fallbackPauseStatus,
            TaskReadCertificationStatus loadTestStatus,
            String evidenceJson) {}
}
