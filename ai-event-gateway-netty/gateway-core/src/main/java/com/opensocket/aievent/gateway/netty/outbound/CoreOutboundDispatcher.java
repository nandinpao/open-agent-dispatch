package com.opensocket.aievent.gateway.netty.outbound;

import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bounded asynchronous Core outbound dispatcher.
 *
 * <p>Context is captured on the submitting thread before the work enters the bounded queue. The
 * captured observation, trace, baggage and MDC context is restored only for the worker execution
 * and completion callback. The worker thread's previous context is restored when the scope closes,
 * preventing thread-reuse leakage.</p>
 */
@org.springframework.stereotype.Service
public class CoreOutboundDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CoreOutboundDispatcher.class);
    private static final String OBSERVATION_NAME = "gateway.core.outbound";

    private final CoreOutboundProperties properties;
    private final ThreadPoolExecutor executor;
    private final RestClient restClient;
    private final ContextSnapshotFactory contextSnapshotFactory;
    private final ObservationRegistry observationRegistry;
    private final Timer queueWaitTimer;
    private final Timer executionTimer;
    private final Counter rejectedMeter;
    private final Counter callbackFailureMeter;
    private final MeterRegistry meterRegistry;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    @Autowired
    public CoreOutboundDispatcher(
            CoreOutboundProperties properties,
            @Qualifier("coreOutboundRestClient") RestClient restClient,
            ObservationRegistry observationRegistry,
            MeterRegistry meterRegistry,
            @Value("${spring.application.name:ai-event-gateway-netty}") String serviceName
    ) {
        this(properties, restClient, observationRegistry, meterRegistry, serviceName,
                ContextSnapshotFactory.builder().clearMissing(true).build());
    }

    /** Test-only convenience constructor. Production wiring must use the Spring-managed constructor. */
    public CoreOutboundDispatcher(CoreOutboundProperties properties) {
        this(properties, RestClient.builder().build(), ObservationRegistry.NOOP,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), "core-outbound-test",
                ContextSnapshotFactory.builder().clearMissing(true).build());
    }

    CoreOutboundDispatcher(
            CoreOutboundProperties properties,
            RestClient restClient,
            ObservationRegistry observationRegistry,
            MeterRegistry meterRegistry,
            String serviceName,
            ContextSnapshotFactory contextSnapshotFactory
    ) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.restClient = Objects.requireNonNull(restClient, "restClient is required");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry is required");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry is required");
        this.contextSnapshotFactory = Objects.requireNonNull(contextSnapshotFactory, "contextSnapshotFactory is required");
        this.executor = new ThreadPoolExecutor(
                properties.workerThreads(),
                properties.workerThreads(),
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                new CoreOutboundThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        Tags tags = Tags.of("service", safe(serviceName), "pool", "core-outbound");
        new ExecutorServiceMetrics(this.executor, "opendispatch.core.outbound.executor", tags).bindTo(meterRegistry);
        this.queueWaitTimer = Timer.builder("opendispatch.core.outbound.queue.wait")
                .description("Queue wait duration before a Core outbound task starts")
                .tags(tags)
                .register(meterRegistry);
        this.executionTimer = Timer.builder("opendispatch.core.outbound.execution")
                .description("Execution duration of a Core outbound task including its completion callback")
                .tags(tags)
                .register(meterRegistry);
        this.rejectedMeter = Counter.builder("opendispatch.core.outbound.rejected")
                .description("Number of Core outbound submissions rejected before HTTP execution")
                .tags(tags)
                .register(meterRegistry);
        this.callbackFailureMeter = Counter.builder("opendispatch.core.outbound.callback.failures")
                .description("Number of Core outbound completion callbacks that failed")
                .tags(tags)
                .register(meterRegistry);
    }

    public CoreOutboundSubmission submit(
            String operation,
            CoreOutboundRequest request,
            Consumer<CoreOutboundResult> completionHandler
    ) {
        if (!properties.enabled()) {
            recordRejected();
            return CoreOutboundSubmission.disabled(queueSize(), queueRemainingCapacity());
        }
        if (request == null) {
            recordRejected();
            return CoreOutboundSubmission.failed("HTTP request is required", queueSize(), queueRemainingCapacity());
        }

        ContextSnapshot submissionContext = contextSnapshotFactory.captureAll();
        long submittedNanos = System.nanoTime();
        try {
            executor.execute(() -> runSubmittedTask(
                    submissionContext, submittedNanos, operation, request, completionHandler));
            submitted.incrementAndGet();
            return CoreOutboundSubmission.submitted(queueSize(), queueRemainingCapacity());
        }
        catch (RejectedExecutionException ex) {
            recordRejected();
            log.warn("Core outbound queue is full. operation={}, queueSize={}, remainingCapacity={}",
                    operation, queueSize(), queueRemainingCapacity());
            return CoreOutboundSubmission.queueFull(queueSize(), queueRemainingCapacity());
        }
        catch (RuntimeException ex) {
            recordRejected();
            log.warn("Core outbound submission failed. operation={}, reason={}", operation, ex.getMessage());
            return CoreOutboundSubmission.failed(ex.getMessage(), queueSize(), queueRemainingCapacity());
        }
    }


    public CoreOutboundResult executeSynchronously(String operation, CoreOutboundRequest request) {
        if (!properties.enabled()) {
            return CoreOutboundResult.failed("Core outbound dispatcher is disabled", Duration.ZERO, null);
        }
        if (request == null) {
            return CoreOutboundResult.failed("HTTP request is required", Duration.ZERO, null);
        }
        return executeObserved(operation, request, null);
    }

    public int queueSize() { return executor.getQueue().size(); }
    public int queueRemainingCapacity() { return executor.getQueue().remainingCapacity(); }
    public int activeCount() { return executor.getActiveCount(); }
    public long submittedCount() { return submitted.get(); }
    public long rejectedCount() { return rejected.get(); }
    public long completedCount() { return completed.get(); }
    public long failedCount() { return failed.get(); }

    private void runSubmittedTask(
            ContextSnapshot submissionContext,
            long submittedNanos,
            String operation,
            CoreOutboundRequest request,
            Consumer<CoreOutboundResult> completionHandler
    ) {
        queueWaitTimer.record(System.nanoTime() - submittedNanos, TimeUnit.NANOSECONDS);
        Timer.Sample execution = Timer.start(meterRegistry);
        try (ContextSnapshot.Scope ignored = submissionContext.setThreadLocals()) {
            executeObserved(operation, request, completionHandler);
        }
        finally {
            execution.stop(executionTimer);
        }
    }

    private CoreOutboundResult executeObserved(
            String operation,
            CoreOutboundRequest request,
            Consumer<CoreOutboundResult> completionHandler
    ) {
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
                .contextualName("core " + operationFamily(operation))
                .lowCardinalityKeyValue("gateway.core.outbound.operation", operationFamily(operation))
                .highCardinalityKeyValue("gateway.core.outbound.operation.original", safe(operation))
                .highCardinalityKeyValue("server.address", safe(request.uri().getHost()))
                .highCardinalityKeyValue("url.path", safe(request.uri().getPath()))
                .start();

        CoreOutboundResult result;
        try (Observation.Scope ignored = observation.openScope()) {
            result = executeHttp(request);
            observation.lowCardinalityKeyValue("gateway.core.outbound.result", result.status().name().toLowerCase());
            if (result.error() != null) {
                observation.error(result.error());
            }

            if (result.success2xx()) {
                completed.incrementAndGet();
            }
            else {
                failed.incrementAndGet();
            }
            Counter.builder("opendispatch.core.outbound.results")
                    .description("Core outbound HTTP execution results")
                    .tag("result", result.status().name().toLowerCase())
                    .register(meterRegistry)
                    .increment();

            invokeCompletion(operation, result, completionHandler);
            return result;
        }
        catch (RuntimeException | Error failure) {
            observation.error(failure);
            failed.incrementAndGet();
            throw failure;
        }
        finally {
            observation.stop();
        }
    }

    private CoreOutboundResult executeHttp(CoreOutboundRequest request) {
        Instant startedAt = Instant.now();
        try {
            RestClient.RequestBodySpec requestSpec = restClient
                    .method(HttpMethod.valueOf(request.method()))
                    .uri(request.uri())
                    .headers(headers -> request.headers().forEach(headers::set));
            RestClient.RequestHeadersSpec<?> exchangeSpec = request.body().isEmpty()
                    ? requestSpec
                    : requestSpec.body(request.body());
            HttpExchangeResult response = exchangeSpec.exchange((clientRequest, clientResponse) ->
                    new HttpExchangeResult(
                            clientResponse.getStatusCode().value(),
                            StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8)));
            return CoreOutboundResult.completed(response.statusCode(), response.body(), elapsed(startedAt));
        }
        catch (ResourceAccessException ex) {
            if (hasCause(ex, HttpTimeoutException.class)) {
                return CoreOutboundResult.timeout(ex.getMessage(), elapsed(startedAt), ex);
            }
            if (hasCause(ex, InterruptedException.class) || Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                return CoreOutboundResult.interrupted(elapsed(startedAt), ex);
            }
            return CoreOutboundResult.failed(ex.getMessage(), elapsed(startedAt), ex);
        }
        catch (RuntimeException ex) {
            return CoreOutboundResult.failed(ex.getMessage(), elapsed(startedAt), ex);
        }
    }

    private void invokeCompletion(
            String operation,
            CoreOutboundResult result,
            Consumer<CoreOutboundResult> completionHandler
    ) {
        if (completionHandler == null) {
            return;
        }
        try {
            completionHandler.accept(result);
        }
        catch (RuntimeException ex) {
            callbackFailureMeter.increment();
            log.warn("Core outbound completion handler failed. operation={}, reason={}", operation, ex.getMessage());
        }
    }

    private void recordRejected() {
        rejected.incrementAndGet();
        rejectedMeter.increment();
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration elapsed(Instant startedAt) { return Duration.between(startedAt, Instant.now()); }

    private String operationFamily(String operation) {
        String value = safe(operation).toLowerCase();
        if (value.startsWith("directory sync")) return "directory_sync";
        if (value.startsWith("task callback relay")) return "task_callback";
        if (value.startsWith("inbound event forward")) return "inbound_event";
        return "other";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(properties.shutdownWaitMs(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private record HttpExchangeResult(int statusCode, String body) {}

    private static final class CoreOutboundThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "core-outbound-worker-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
