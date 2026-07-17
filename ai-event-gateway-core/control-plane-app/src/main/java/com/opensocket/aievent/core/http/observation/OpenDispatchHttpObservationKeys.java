package com.opensocket.aievent.core.http.observation;

public final class OpenDispatchHttpObservationKeys {
    public static final String REQUEST_ID = "opendispatch.request.id";
    public static final String CORRELATION_ID = "opendispatch.correlation.id";
    public static final String TENANT_ID = "opendispatch.tenant.id";
    public static final String OPERATOR_ID = "opendispatch.operator.id";
    public static final String CLIENT_ADDRESS = "opendispatch.client.address";
    public static final String USER_AGENT = "opendispatch.user_agent.original";

    public static final String REQUEST_KIND = "opendispatch.request.kind";
    public static final String AUTHENTICATED = "opendispatch.security.authenticated";
    public static final String TENANT_PRESENT = "opendispatch.tenant.present";

    public static final String ATTR_PREFIX = OpenDispatchHttpObservationKeys.class.getName() + ".";

    private OpenDispatchHttpObservationKeys() {
    }
}
