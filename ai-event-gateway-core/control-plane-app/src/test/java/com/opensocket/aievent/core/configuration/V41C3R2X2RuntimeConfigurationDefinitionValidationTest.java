package com.opensocket.aievent.core.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class V41C3R2X2RuntimeConfigurationDefinitionValidationTest {
    @Test
    void enumAllowedValuesAreEnforcedBeforeRevisionCreation() throws Exception {
        Method method = method("validateEnum");
        Map<String,Object> rule = Map.of("allowedValues", List.of("CREATE_NEW", "REOPEN_RECENT"));
        assertThatCode(() -> invoke(method, "REOPEN_RECENT", rule, "core.lifecycle.incident.reopen-policy"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> invoke(method, "NOT_A_POLICY", rule, "core.lifecycle.incident.reopen-policy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be one of");
    }

    @Test
    void stringPatternAndCsvAllowedValuesAreEnforced() throws Exception {
        Method method = method("validateString");
        assertThatThrownBy(() -> invoke(method, "relative/path", Map.of("pattern", "^/"), "dispatch.gateway-dispatch-path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required pattern");
        assertThatThrownBy(() -> invoke(method, "MCP,UNKNOWN", Map.of("allowedCsvValues", List.of("MCP"), "minItems", 1), "adapter-worker.adapter-types"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported value");
    }

    @Test
    void uriAllowedSchemesAndLengthAreEnforced() throws Exception {
        Method method = RuntimeConfigurationAdminService.class.getDeclaredMethod("validateUri", Object.class, Map.class, String.class);
        method.setAccessible(true);
        Map<String,Object> rule = Map.of("allowedSchemes", List.of("http", "https"), "maxLength", 2048);
        assertThatCode(() -> invoke(method, "https://gateway.example.test", rule, "dispatch.client.gateway-base-urls.gateway-tpe-001"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> invoke(method, "ftp://gateway.example.test", rule, "dispatch.client.gateway-base-urls.gateway-tpe-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URI scheme");
    }

    private static Method method(String name) throws Exception {
        Method method = RuntimeConfigurationAdminService.class.getDeclaredMethod(name, Object.class, Map.class, String.class);
        method.setAccessible(true);
        return method;
    }

    private static void invoke(Method method, Object value, Map<String,Object> rule, String key) throws Throwable {
        try {
            method.invoke(null, value, rule, key);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }
}
