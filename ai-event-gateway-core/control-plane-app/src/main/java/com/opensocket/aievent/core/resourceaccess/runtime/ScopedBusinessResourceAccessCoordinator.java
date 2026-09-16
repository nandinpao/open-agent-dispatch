package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Canonical P2.3B REST adapter for scoped business configuration resources. */
@Component
@ConditionalOnProperty(prefix="resource-access", name={"enabled","business-data-enabled"}, havingValue="true")
public final class ScopedBusinessResourceAccessCoordinator {
    private final ResourceAccessEnforcementPort enforcement;
    private final ResourceListScopeQueryPlanPort plans;
    private final ResourceEnforcementContextPort contexts;

    public ScopedBusinessResourceAccessCoordinator(ResourceAccessEnforcementPort enforcement,
            ResourceListScopeQueryPlanPort plans, ResourceEnforcementContextPort contexts) {
        this.enforcement = Objects.requireNonNull(enforcement);
        this.plans = Objects.requireNonNull(plans);
        this.contexts = Objects.requireNonNull(contexts);
    }

    public ResourceListScopeQueryPlan plan(String permission, ResourceType type, VisibilityLevel visibility, String purpose) {
        return plans.build(permission, type, visibility, purpose);
    }

    /** Active Tenant is session authority. Human REST query parameters never switch workspace authority. */
    public String activeTenantId() {
        return contexts.current().authentication().activeTenant().tenantId();
    }

    public ResourceListScopeQueryPlan requireTenantWide(String permission, ResourceType type, VisibilityLevel visibility, String purpose) {
        ResourceListScopeQueryPlan plan = plan(permission, type, visibility, purpose);
        if (!plan.tenantWide()) {
            throw new com.opensocket.aievent.core.api.StandardApiException(
                    com.opensocket.aievent.core.api.StandardApiErrorCode.FORBIDDEN,
                    "This operation requires Tenant-wide authority; Department / Group scope is not sufficient.");
        }
        return plan;
    }

    public AuthorizationDecision authorize(ResourceType type, String id, String permission,
            ResourceAction.ActionKind kind, boolean sideEffecting, VisibilityLevel visibility, String purpose) {
        var runtime = contexts.current();
        return enforcement.authorize(new ResourceEnforcementCommand(
                new ResourceAction(required(permission,"permission"), kind, sideEffecting),
                new ResourceRef(runtime.authentication().activeTenant().tenantId(), type, required(id,"resourceId")),
                visibility, RequestChannel.REST, required(purpose,"purpose"),
                sideEffecting ? OperationPhase.BEFORE_SIDE_EFFECT : OperationPhase.START,
                SecurityEpoch.ZERO, Map.of("p2_3b","SCOPED_BUSINESS_DATA")));
    }

    private static String required(String value,String field){
        if(value==null||value.isBlank()) throw new IllegalArgumentException(field+" is required");
        return value.trim();
    }
}
