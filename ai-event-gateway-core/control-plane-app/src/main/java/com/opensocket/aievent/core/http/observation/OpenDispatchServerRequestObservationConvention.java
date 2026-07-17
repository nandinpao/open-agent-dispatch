package com.opensocket.aievent.core.http.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.http.server.observation.OpenTelemetryServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/** Adds bounded OpenDispatch dimensions and trace-only request identity to Spring's HTTP observation. */
public class OpenDispatchServerRequestObservationConvention extends OpenTelemetryServerRequestObservationConvention {
    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        return super.getLowCardinalityKeyValues(context).and(
                KeyValue.of(OpenDispatchHttpObservationKeys.REQUEST_KIND, attribute(context, OpenDispatchHttpObservationKeys.REQUEST_KIND, "other")),
                KeyValue.of(OpenDispatchHttpObservationKeys.AUTHENTICATED, attribute(context, OpenDispatchHttpObservationKeys.AUTHENTICATED, "false")),
                KeyValue.of(OpenDispatchHttpObservationKeys.TENANT_PRESENT, attribute(context, OpenDispatchHttpObservationKeys.TENANT_PRESENT, "false")));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
        return super.getHighCardinalityKeyValues(context).and(
                KeyValue.of(OpenDispatchHttpObservationKeys.REQUEST_ID, attribute(context, OpenDispatchHttpObservationKeys.REQUEST_ID, "none")),
                KeyValue.of(OpenDispatchHttpObservationKeys.CORRELATION_ID, attribute(context, OpenDispatchHttpObservationKeys.CORRELATION_ID, "none")),
                KeyValue.of(OpenDispatchHttpObservationKeys.TENANT_ID, attribute(context, OpenDispatchHttpObservationKeys.TENANT_ID, "none")),
                KeyValue.of(OpenDispatchHttpObservationKeys.OPERATOR_ID, attribute(context, OpenDispatchHttpObservationKeys.OPERATOR_ID, "anonymous")),
                KeyValue.of(OpenDispatchHttpObservationKeys.CLIENT_ADDRESS, attribute(context, OpenDispatchHttpObservationKeys.CLIENT_ADDRESS, "unknown")),
                KeyValue.of(OpenDispatchHttpObservationKeys.USER_AGENT, attribute(context, OpenDispatchHttpObservationKeys.USER_AGENT, "unknown")));
    }

    private String attribute(ServerRequestObservationContext context, String key, String fallback) {
        if (context.getCarrier() == null) {
            return fallback;
        }
        Object value = context.getCarrier().getAttribute(OpenDispatchHttpObservationKeys.ATTR_PREFIX + key);
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }
}
