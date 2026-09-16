package com.opensocket.aievent.core.uicapability.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal Phase 7A-2 page catalog. Additional domains are introduced only after the Task pilot is certified. */
public final class CanonicalUiPageCatalogResolver implements UiPageCatalogResolver {
    private static final Map<String, UiPageDefinition> PAGES = Map.of(
            "task.detail", new UiPageDefinition(
                    "task.detail", "/tasks/{resourceId}", "task.detail.view",
                    List.of("task.detail.view", "task.detail.update", "task.participant.manage"))
    );
    @Override public Optional<UiPageDefinition> resolve(String routeContext) {
        return Optional.ofNullable(PAGES.get(routeContext));
    }
}
