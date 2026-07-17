package com.opensocket.aievent.core.http.context;

import io.micrometer.context.ThreadLocalAccessor;

/** Makes the typed OpenDispatch request context available to ContextSnapshot. */
public final class OpenDispatchRequestContextThreadLocalAccessor
        implements ThreadLocalAccessor<OpenDispatchRequestContext> {

    public static final String KEY = "opendispatch.request-context";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public OpenDispatchRequestContext getValue() {
        return OpenDispatchRequestContextHolder.getValue();
    }

    @Override
    public void setValue(OpenDispatchRequestContext value) {
        OpenDispatchRequestContextHolder.setValue(value);
    }

    @Override
    public void setValue() {
        OpenDispatchRequestContextHolder.clear();
    }
}
