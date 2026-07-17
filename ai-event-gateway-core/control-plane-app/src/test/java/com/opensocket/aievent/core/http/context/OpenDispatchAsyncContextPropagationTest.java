package com.opensocket.aievent.core.http.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

class OpenDispatchAsyncContextPropagationTest {

    @AfterEach
    void cleanup() {
        OpenDispatchRequestContextHolder.clear();
        MDC.clear();
    }

    @Test
    void propagatesRequestAndMdcContextAndDoesNotLeakAcrossThreadReuse() throws Exception {
        ContextRegistry registry = new ContextRegistry();
        registry.registerThreadLocalAccessor(new OpenDispatchRequestContextThreadLocalAccessor());
        registry.registerThreadLocalAccessor(new Slf4jThreadLocalAccessor(
                OpenDispatchContextPropagationConfiguration.PROPAGATED_MDC_KEYS));
        ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator(
                ContextSnapshotFactory.builder().contextRegistry(registry).build());

        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "core-async-context-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            OpenDispatchRequestContext first = new OpenDispatchRequestContext(
                    "request-1", "correlation-1", "tenant-a", "operator-a", "127.0.0.1", "test", "api");
            try (OpenDispatchRequestContextHolder.Scope ignored = OpenDispatchRequestContextHolder.open(first)) {
                MDC.setContextMap(Map.of(
                        "traceId", "trace-a",
                        "taskId", "task-a",
                        "tenantId", "tenant-a",
                        "operatorId", "operator-a"));

                AsyncState propagated = submit(executor, decorator);
                assertThat(propagated.requestContext()).isEqualTo(first);
                assertThat(propagated.mdc()).containsEntry("traceId", "trace-a")
                        .containsEntry("taskId", "task-a")
                        .containsEntry("tenantId", "tenant-a")
                        .containsEntry("operatorId", "operator-a");
            }
            MDC.clear();

            AsyncState clean = submit(executor, decorator);
            assertThat(clean.requestContext()).isNull();
            assertThat(clean.mdc()).isNullOrEmpty();
        }
        finally {
            executor.shutdownNow();
        }
    }

    private AsyncState submit(ExecutorService executor, ContextPropagatingTaskDecorator decorator) throws Exception {
        AtomicReference<AsyncState> state = new AtomicReference<>();
        executor.submit(decorator.decorate(() -> state.set(new AsyncState(
                OpenDispatchRequestContextHolder.current().orElse(null),
                MDC.getCopyOfContextMap())))).get(5, TimeUnit.SECONDS);
        return state.get();
    }

    private record AsyncState(OpenDispatchRequestContext requestContext, Map<String, String> mdc) {
    }
}
