package com.opensocket.aievent.core.uicapability.core;

import java.util.List;

/** Server-owned route definition. Client input never selects permissions or resource types. */
public record UiPageDefinition(
        String routeContext,
        String canonicalPathTemplate,
        String viewActionId,
        List<String> pageActionIds) {
    public UiPageDefinition {
        if (routeContext == null || routeContext.isBlank()) throw new IllegalArgumentException("routeContext is required");
        if (canonicalPathTemplate == null || !canonicalPathTemplate.startsWith("/")) throw new IllegalArgumentException("canonicalPathTemplate is required");
        if (viewActionId == null || viewActionId.isBlank()) throw new IllegalArgumentException("viewActionId is required");
        pageActionIds = pageActionIds == null ? List.of() : List.copyOf(pageActionIds);
        if (pageActionIds.isEmpty() || !pageActionIds.contains(viewActionId)) throw new IllegalArgumentException("pageActionIds must include viewActionId");
    }
    public String canonicalPath(String resourceId) {
        String encoded = java.net.URLEncoder.encode(resourceId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return canonicalPathTemplate.replace("{resourceId}", encoded);
    }
}
