package com.opensocket.aievent.core.iam.persistence.tenant;

import java.util.Optional;
import java.util.function.Supplier;

/** Request-scoped bridge. The HTTP adapter must populate it from authenticated server state, never request JSON. */
public final class IamTenantContextHolder {
    private static final ThreadLocal<IamTenantExecutionContext> CURRENT = new ThreadLocal<>();
    private IamTenantContextHolder() {}
    public static Optional<IamTenantExecutionContext> current() { return Optional.ofNullable(CURRENT.get()); }
    public static IamTenantExecutionContext require() {
        return current().orElseThrow(() -> new IllegalStateException("TENANT_CONTEXT_REQUIRED"));
    }
    public static <T> T withContext(IamTenantExecutionContext context, Supplier<T> action) {
        try (Scope ignored = open(context)) { return action.get(); }
    }
    public static Scope open(IamTenantExecutionContext context) {
        IamTenantExecutionContext previous = CURRENT.get();
        CURRENT.set(java.util.Objects.requireNonNull(context, "context"));
        return new Scope(previous);
    }
    public static void clear() { CURRENT.remove(); }
    public static final class Scope implements AutoCloseable {
        private final IamTenantExecutionContext previous;
        private boolean closed;
        private Scope(IamTenantExecutionContext previous) { this.previous = previous; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
