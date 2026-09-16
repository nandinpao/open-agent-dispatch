package com.opensocket.aievent.core.uicapability.runtime;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;
import com.opensocket.aievent.core.uicapability.contract.UiCapabilityApiPaths;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Keeps the declared UI Capability HTTP contract materialized when projection authority is disabled.
 * The Admin UI may always know this product route; a disabled feature must therefore fail as a typed
 * dependency state instead of falling through Spring MVC static-resource handling.
 */
@RestController
@ConditionalOnExpression("!${ui-capability.enabled:false} || !${ui-capability.projection-api-enabled:false}")
public final class UiCapabilityDisabledFallbackController {
    @PostMapping({UiCapabilityApiPaths.CAPABILITY_BATCH, UiCapabilityApiPaths.LIST_CAPABILITY_BATCH})
    public void disabled() {
        throw new StandardApiException(StandardApiErrorCode.DEPENDENCY_UNAVAILABLE,
                "UI_CAPABILITY_PROJECTION_DISABLED: Formal UI Capability projection authority is not enabled.");
    }
}
