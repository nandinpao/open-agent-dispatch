package com.opensocket.aievent.core.dispatch.flow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Owns JSON serialization concerns for the Dispatch Flow aggregate.
 *
 * <p>This support class deliberately remains package-private. The public
 * authority stays in {@link DispatchFlowManagementService}; extraction only
 * separates persistence representation from command/query orchestration.</p>
 */
final class DispatchFlowJsonCodec {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    DispatchFlowJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Unable to serialize Dispatch Flow JSON", ex);
        }
    }

    Map<String, Object> readMap(String value) {
        if (blank(value)) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    List<String> readStringList(String value) {
        if (blank(value)) return new ArrayList<>();
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
