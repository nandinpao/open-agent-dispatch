package com.opensocket.aievent.core.security;

import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.rbac.domain.LegacyDecision;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Evidence-only evaluator for the retired Human Admin role rules.
 * It never grants access and is used solely to persist MATCH/MISMATCH evidence.
 */
public final class R3CompatibilityDecisionEvaluator {
    private final IamApiRuntimeDao dao;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public R3CompatibilityDecisionEvaluator(
            IamApiRuntimeDao dao,
            TransactionTemplate transactions,
            Clock clock) {
        this.dao = dao;
        this.transactions = transactions;
        this.clock = clock;
    }

    public LegacyDecision evaluate(AuthenticationContext context, Set<String> acceptedLegacyRoles) {
        if (acceptedLegacyRoles == null || acceptedLegacyRoles.isEmpty()) return LegacyDecision.DENY;
        Set<String> projected = projectedEvidenceRoles(context);
        for (String role : acceptedLegacyRoles) {
            if (projected.contains(role.toUpperCase(Locale.ROOT))) return LegacyDecision.ALLOW;
        }
        return LegacyDecision.DENY;
    }

    private Set<String> projectedEvidenceRoles(AuthenticationContext context) {
        Set<String> result = new LinkedHashSet<>();
        if (context.principal().principalType() == PrincipalRef.PrincipalType.INSTANCE_ROOT) {
            result.addAll(Set.of("ADMIN", "OPERATOR", "VIEWER", "SUPPORT"));
            return result;
        }
        if (context.activeTenant().scope() != TenantRef.Scope.TENANT) return result;
        String tenantId = context.activeTenant().tenantId();
        List<String> canonical = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext(tenantId, "r3-shadow:" + context.subject().subjectId()),
                () -> transactions.execute(status -> dao.activeTenantAuthorityRoleCodes(
                        tenantId, context.subject().subjectId(), clock.instant())));
        if (canonical != null) canonical.forEach(role -> map(role, result));
        return result;
    }

    private static void map(String value, Set<String> target) {
        String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        switch (role) {
            case "SYSTEM_ADMIN", "PLATFORM_ADMIN", "TENANT_ADMIN", "SECURITY_ADMIN", "ACCESS_ADMIN" -> {
                target.add("ADMIN"); target.add("OPERATOR"); target.add("VIEWER");
            }
            case "ORGANIZATION_ADMIN", "USER_ADMIN", "OPERATOR" -> {
                target.add("OPERATOR"); target.add("VIEWER");
            }
            case "RECOVERY_ADMIN" -> {
                target.add("RECOVERY_ADMIN"); target.add("RECOVERY_OPERATOR"); target.add("OPERATOR");
            }
            case "RECOVERY_OPERATOR" -> { target.add("RECOVERY_OPERATOR"); target.add("OPERATOR"); }
            case "RECOVERY_APPROVER" -> target.add("RECOVERY_APPROVER");
            case "SUPPORT" -> target.add("SUPPORT");
            case "AUDITOR", "VIEWER", "IDENTITY_VIEWER" -> target.add("VIEWER");
            default -> { /* no compatibility meaning */ }
        }
    }
}
