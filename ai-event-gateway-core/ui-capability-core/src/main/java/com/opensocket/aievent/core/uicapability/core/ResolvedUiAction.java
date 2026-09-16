package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.uicapability.contract.UiActionDefinition;
import java.util.Objects;

public record ResolvedUiAction(
        UiActionDefinition definition,
        ResourceType resourceType,
        ResourceAction resourceAction) {
    public ResolvedUiAction {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceAction, "resourceAction");
        if (!definition.canonicalPermissionCode().equals(resourceAction.permissionCode())) {
            throw new IllegalArgumentException("action permission drift: " + definition.uiActionId());
        }
        if (!definition.resourceType().equals(resourceType.name())) {
            throw new IllegalArgumentException("action resource type drift: " + definition.uiActionId());
        }
    }
}
