package com.opensocket.aievent.core.http.context;

import java.util.Optional;

/** Thread-bound request context propagated through Micrometer Context Propagation. */
public final class OpenDispatchRequestContextHolder {
    private static final ThreadLocal<OpenDispatchRequestContext> CONTEXT = new ThreadLocal<>();

    private OpenDispatchRequestContextHolder() {
    }

    public static Optional<OpenDispatchRequestContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    static OpenDispatchRequestContext getValue() {
        return CONTEXT.get();
    }

    static void setValue(OpenDispatchRequestContext context) {
        if (context == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(context);
        }
    }

    public static Scope open(OpenDispatchRequestContext context) {
        OpenDispatchRequestContext previous = CONTEXT.get();
        if (context == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(context);
        }
        return new Scope(previous);
    }

    public static Scope enrichBusinessContext(String tenantId, String correlationId) {
        OpenDispatchRequestContext previous = CONTEXT.get();
        if (previous != null) {
            CONTEXT.set(previous.withBusinessContext(tenantId, correlationId));
        }
        return new Scope(previous);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static final class Scope implements AutoCloseable {
        private final OpenDispatchRequestContext previous;
        private boolean closed;

        private Scope(OpenDispatchRequestContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }
}
