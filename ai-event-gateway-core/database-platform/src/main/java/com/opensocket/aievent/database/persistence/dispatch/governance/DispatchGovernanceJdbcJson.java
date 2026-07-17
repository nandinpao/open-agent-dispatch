package com.opensocket.aievent.database.persistence.dispatch.governance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class DispatchGovernanceJdbcJson {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public DispatchGovernanceJdbcJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to serialize dispatch governance JSON", ex);
        }
    }

    public Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    public List<String> readStringList(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }
}
