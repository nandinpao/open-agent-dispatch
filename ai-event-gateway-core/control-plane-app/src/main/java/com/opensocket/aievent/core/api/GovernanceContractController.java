package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.governance.AuditEvidence;
import com.opensocket.aievent.core.governance.AuditEvidenceService;
import com.opensocket.aievent.core.governance.ReasonCodeCatalog;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PermissionCatalogRepository;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/governance")
public class GovernanceContractController {
    private final AuditEvidenceService audit;
    private final PermissionCatalogRepository permissions;

    public GovernanceContractController(AuditEvidenceService audit, PermissionCatalogRepository permissions) {
        this.audit = audit;
        this.permissions = permissions;
    }

    @GetMapping("/permission-points")
    public Set<String> permissions() {
        return permissions.findActiveCodes();
    }

    @GetMapping("/reason-codes")
    public Set<String> reasons() {
        return ReasonCodeCatalog.ALL;
    }

    @GetMapping("/audit-evidence")
    public List<AuditEvidence> evidence(
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(defaultValue = "200") int limit) {
        return audit.evidence(tenant(), aggregateType, aggregateId, limit);
    }

    private String tenant() {
        String value = OpenDispatchRequestContextHolder.current()
                .map(OpenDispatchRequestContext::tenantId).orElse(null);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        return value;
    }
}
