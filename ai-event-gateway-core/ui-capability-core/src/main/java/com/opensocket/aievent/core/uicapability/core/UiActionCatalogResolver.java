package com.opensocket.aievent.core.uicapability.core;

import java.util.Optional;

/** Resolves only server-owned action metadata. Browser requests never supply permission or resource type. */
public interface UiActionCatalogResolver {
    Optional<ResolvedUiAction> resolve(String uiActionId);
}
