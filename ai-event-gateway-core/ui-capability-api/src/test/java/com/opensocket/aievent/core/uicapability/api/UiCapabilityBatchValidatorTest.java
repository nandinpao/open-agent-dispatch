package com.opensocket.aievent.core.uicapability.api;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.uicapability.contract.*;
import com.opensocket.aievent.core.uicapability.core.UiCapabilityProjectionException;
import java.util.*;
import org.junit.jupiter.api.Test;

class UiCapabilityBatchValidatorTest {
    @Test void genericLimitIsTwenty() {
        UiCapabilityProjectionException error = assertThrows(UiCapabilityProjectionException.class,
                () -> validate(contexts(21, List.of("a2a.request.read")), UiCapabilityBatchValidator.Profile.GENERIC));
        assertEquals(UiCapabilityProjectionException.Code.UI_CAPABILITY_BATCH_LIMIT_EXCEEDED, error.code());
    }

    @Test void listLimitIsFifty() {
        assertDoesNotThrow(() -> validate(contexts(50, List.of("a2a.request.read")), UiCapabilityBatchValidator.Profile.LIST_ROW));
    }

    @Test void listRowActionLimitIsEight() {
        List<String> actions = new ArrayList<>();
        for (int i = 0; i < 9; i++) actions.add("test.row.action-" + i);
        UiCapabilityProjectionException error = assertThrows(UiCapabilityProjectionException.class,
                () -> validate(contexts(1, actions), UiCapabilityBatchValidator.Profile.LIST_ROW));
        assertEquals(UiCapabilityProjectionException.Code.UI_CAPABILITY_BATCH_LIMIT_EXCEEDED, error.code());
    }

    @Test void duplicateContextIdIsRejected() {
        UiCapabilityContextRequest one = new UiCapabilityContextRequest("same", "r-1", null, null, List.of("a2a.request.read"));
        UiCapabilityContextRequest two = new UiCapabilityContextRequest("same", "r-2", null, null, List.of("a2a.request.read"));
        UiCapabilityProjectionException error = assertThrows(UiCapabilityProjectionException.class,
                () -> validate(List.of(one, two), UiCapabilityBatchValidator.Profile.GENERIC));
        assertEquals(UiCapabilityProjectionException.Code.UI_CAPABILITY_CONTEXT_INVALID, error.code());
    }

    private static void validate(List<UiCapabilityContextRequest> contexts, UiCapabilityBatchValidator.Profile profile) {
        new UiCapabilityBatchValidator().validate(new UiCapabilityBatchRequest("1.0", contexts), profile);
    }

    private static List<UiCapabilityContextRequest> contexts(int count, List<String> actions) {
        List<UiCapabilityContextRequest> contexts = new ArrayList<>();
        for (int i = 0; i < count; i++) contexts.add(new UiCapabilityContextRequest("ctx-" + i, "r-" + i, null, null, actions));
        return contexts;
    }
}
