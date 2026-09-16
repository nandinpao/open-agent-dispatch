package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backend-owned UI entitlement projection. React consumes Navigator/Page/Action
 * presentation decisions from this contract instead of rebuilding authorization
 * from Role names or raw Permission codes.
 */
public record UiEntitlementResponse(
        String contractVersion,
        String workspaceKind,
        String tenantId,
        List<NavigationItem> navigation,
        Map<String, PageEntitlement> pages,
        Set<String> actions,
        Map<String, ActionEntitlement> actionEntitlements,
        Map<String, Set<String>> actionScopes,
        Instant generatedAt) {

    public UiEntitlementResponse {
        navigation = navigation == null ? List.of() : List.copyOf(navigation);
        pages = pages == null ? Map.of() : Map.copyOf(pages);
        actions = actions == null ? Set.of() : Set.copyOf(actions);
        actionEntitlements = actionEntitlements == null ? Map.of() : Map.copyOf(actionEntitlements);
        actionScopes = actionScopes == null ? Map.of() : actionScopes.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    /**
     * Navigation is a product-owned hierarchy. `route` may contain `{tenantId}`;
     * the browser substitutes only the active canonical Tenant identifier.
     */
    public record NavigationItem(
            String featureId,
            String parentFeatureId,
            String section,
            int order,
            String route,
            String label,
            String purpose,
            String displayMode,
            List<NavigationItem> children) {
        public NavigationItem {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    /**
     * `allowed` is retained during the 2.x -> 3.0 migration as a compatibility
     * projection. `displayMode` is authoritative for browser presentation.
     */
    public record PageEntitlement(
            String featureId,
            String route,
            String displayMode,
            boolean allowed,
            String denialReason,
            String surface) {}

    public record ActionEntitlement(
            String actionId,
            String displayMode,
            Set<String> scopes) {
        public ActionEntitlement {
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        }
    }
}
