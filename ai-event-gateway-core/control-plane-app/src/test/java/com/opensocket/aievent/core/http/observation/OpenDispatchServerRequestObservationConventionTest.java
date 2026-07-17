package com.opensocket.aievent.core.http.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OpenDispatchServerRequestObservationConventionTest {
    private final OpenDispatchServerRequestObservationConvention convention =
            new OpenDispatchServerRequestObservationConvention();

    @Test
    void shouldAddBoundedAndTraceOnlyRequestMetadataToOpenTelemetryConvention() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/agents");
        set(request, OpenDispatchHttpObservationKeys.REQUEST_KIND, "admin");
        set(request, OpenDispatchHttpObservationKeys.AUTHENTICATED, "true");
        set(request, OpenDispatchHttpObservationKeys.TENANT_PRESENT, "true");
        set(request, OpenDispatchHttpObservationKeys.REQUEST_ID, "request-001");
        set(request, OpenDispatchHttpObservationKeys.CORRELATION_ID, "correlation-001");
        set(request, OpenDispatchHttpObservationKeys.TENANT_ID, "tenant-a");
        set(request, OpenDispatchHttpObservationKeys.OPERATOR_ID, "operator-a");
        set(request, OpenDispatchHttpObservationKeys.CLIENT_ADDRESS, "192.0.2.10");
        set(request, OpenDispatchHttpObservationKeys.USER_AGENT, "OpenDispatch-Test/1.0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ServerRequestObservationContext context = new ServerRequestObservationContext(request, response);
        context.setPathPattern("/admin/agents");

        Map<String, String> low = asMap(convention.getLowCardinalityKeyValues(context));
        Map<String, String> high = asMap(convention.getHighCardinalityKeyValues(context));

        assertThat(low).containsEntry(OpenDispatchHttpObservationKeys.REQUEST_KIND, "admin")
                .containsEntry(OpenDispatchHttpObservationKeys.AUTHENTICATED, "true")
                .containsEntry(OpenDispatchHttpObservationKeys.TENANT_PRESENT, "true");
        assertThat(high).containsEntry(OpenDispatchHttpObservationKeys.REQUEST_ID, "request-001")
                .containsEntry(OpenDispatchHttpObservationKeys.CORRELATION_ID, "correlation-001")
                .containsEntry(OpenDispatchHttpObservationKeys.TENANT_ID, "tenant-a")
                .containsEntry(OpenDispatchHttpObservationKeys.OPERATOR_ID, "operator-a")
                .containsEntry(OpenDispatchHttpObservationKeys.CLIENT_ADDRESS, "192.0.2.10")
                .containsEntry(OpenDispatchHttpObservationKeys.USER_AGENT, "OpenDispatch-Test/1.0");
    }

    private void set(MockHttpServletRequest request, String key, String value) {
        request.setAttribute(OpenDispatchHttpObservationKeys.ATTR_PREFIX + key, value);
    }

    private Map<String, String> asMap(KeyValues values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (KeyValue value : values) {
            result.put(value.getKey(), value.getValue());
        }
        return result;
    }
}
