package com.opensocket.aievent.core.http.context;

import java.util.Map;

import org.slf4j.MDC;

/** Restores the exact MDC state that existed before the scope was opened. */
public final class MdcContextScope implements AutoCloseable {
    private final Map<String, String> previous;
    private boolean closed;

    private MdcContextScope(Map<String, String> values) {
        this.previous = MDC.getCopyOfContextMap();
        if (values != null) {
            values.forEach(MdcContextScope::putIfPresent);
        }
    }

    public static MdcContextScope open(Map<String, String> values) {
        return new MdcContextScope(values);
    }

    public static void putIfPresent(String key, String value) {
        if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (previous == null || previous.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }
}
