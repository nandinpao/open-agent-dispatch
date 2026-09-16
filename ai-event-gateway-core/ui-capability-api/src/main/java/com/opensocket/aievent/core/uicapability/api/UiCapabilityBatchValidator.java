package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.uicapability.contract.*;
import com.opensocket.aievent.core.uicapability.core.UiCapabilityProjectionException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

final class UiCapabilityBatchValidator {
    enum Profile { GENERIC, LIST_ROW }
    void validate(UiCapabilityBatchRequest request, Profile profile) {
        int maxContexts = profile == Profile.GENERIC ? UiCapabilityBatchLimits.GENERIC_CONTEXTS
                : UiCapabilityBatchLimits.LIST_ROW_CONTEXTS;
        int maxActions = profile == Profile.GENERIC ? UiCapabilityBatchLimits.GENERIC_ACTIONS_PER_CONTEXT
                : UiCapabilityBatchLimits.LIST_ROW_ACTIONS_PER_CONTEXT;
        int maxBytes = profile == Profile.GENERIC ? UiCapabilityBatchLimits.GENERIC_MAX_PAYLOAD_BYTES
                : UiCapabilityBatchLimits.LIST_ROW_MAX_PAYLOAD_BYTES;
        if (request.contexts().size() > maxContexts) throw error(
                UiCapabilityProjectionException.Code.UI_CAPABILITY_BATCH_LIMIT_EXCEEDED,
                "Capability context batch exceeds the endpoint limit");
        Set<String> contexts = new HashSet<>();
        int bytes = request.contractVersion().getBytes(StandardCharsets.UTF_8).length;
        for (UiCapabilityContextRequest context : request.contexts()) {
            if (!contexts.add(context.contextId())) throw error(
                    UiCapabilityProjectionException.Code.UI_CAPABILITY_CONTEXT_INVALID,
                    "Duplicate contextId in capability batch");
            if (context.uiActionIds().size() > maxActions) throw error(
                    UiCapabilityProjectionException.Code.UI_CAPABILITY_BATCH_LIMIT_EXCEEDED,
                    "Capability action count exceeds the context limit");
            bytes += context.contextId().getBytes(StandardCharsets.UTF_8).length;
            bytes += context.resourceId().getBytes(StandardCharsets.UTF_8).length;
            for (String action : context.uiActionIds()) bytes += action.getBytes(StandardCharsets.UTF_8).length;
        }
        if (bytes > maxBytes) throw error(UiCapabilityProjectionException.Code.UI_CAPABILITY_PAYLOAD_TOO_LARGE,
                "Capability request exceeds the endpoint payload budget");
    }
    private static UiCapabilityProjectionException error(UiCapabilityProjectionException.Code code, String message) {
        return new UiCapabilityProjectionException(code, message);
    }
}
