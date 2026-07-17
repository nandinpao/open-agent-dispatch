package com.opensocket.aievent.gateway.netty.outbound;

import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CoreOutboundDispatcherTest {
    private CoreOutboundDispatcher dispatcher;
    private HttpServer server;

    @AfterEach
    void cleanup() {
        if (dispatcher != null) dispatcher.shutdown();
        if (server != null) server.stop(0);
    }

    @Test
    void shouldCaptureContextAtSubmissionInjectHeadersAndClearWorkerForNextTask() throws Exception {
        ArrayBlockingQueue<Captured> requests = new ArrayBlockingQueue<>(2);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/core", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.offer(new Captured(
                    exchange.getRequestHeaders().getFirst("traceparent"),
                    exchange.getRequestHeaders().getFirst("tracestate"),
                    exchange.getRequestHeaders().getFirst("baggage"), body));
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();

        RestClient client = observableTestClient(Duration.ofSeconds(2));
        ThreadLocal<String> tenant = new ThreadLocal<>();
        ContextRegistry contextRegistry = new ContextRegistry();
        contextRegistry.registerThreadLocalAccessor("test.tenant", tenant);
        ContextSnapshotFactory snapshots = ContextSnapshotFactory.builder()
                .contextRegistry(contextRegistry)
                .clearMissing(true)
                .build();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        dispatcher = new CoreOutboundDispatcher(properties(1, 2), client,
                ObservationRegistry.NOOP, meters, "gateway-test", snapshots);

        ArrayBlockingQueue<String> callbackContexts = new ArrayBlockingQueue<>(2);
        tenant.set("tenant-a");
        dispatcher.submit("inbound event forward", request("first"), result -> callbackContexts.offer(String.valueOf(tenant.get())));
        assertThat(callbackContexts.poll(3, TimeUnit.SECONDS)).isEqualTo("tenant-a");

        tenant.remove();
        dispatcher.submit("inbound event forward", request("second"), result -> callbackContexts.offer(String.valueOf(tenant.get())));
        assertThat(callbackContexts.poll(3, TimeUnit.SECONDS)).isEqualTo("null");

        Captured first = requests.poll(3, TimeUnit.SECONDS);
        Captured second = requests.poll(3, TimeUnit.SECONDS);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.traceparent()).isEqualTo("00-11111111111111111111111111111111-2222222222222222-01");
        assertThat(first.tracestate()).isEqualTo("vendor=value");
        assertThat(first.baggage()).isEqualTo("tenant.id=tenant-a");
        assertThat(meters.get("opendispatch.core.outbound.queue.wait").timer().count()).isEqualTo(2);
        assertThat(meters.get("opendispatch.core.outbound.execution").timer().count()).isEqualTo(2);
    }

    @Test
    void shouldUseTheSameObservableClientForSynchronousExecution() throws Exception {
        ArrayBlockingQueue<Captured> requests = new ArrayBlockingQueue<>(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/core", exchange -> {
            requests.offer(new Captured(
                    exchange.getRequestHeaders().getFirst("traceparent"),
                    exchange.getRequestHeaders().getFirst("tracestate"),
                    exchange.getRequestHeaders().getFirst("baggage"), ""));
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(202, 0);
            exchange.close();
        });
        server.start();
        dispatcher = new CoreOutboundDispatcher(properties(1, 1), observableTestClient(Duration.ofSeconds(2)),
                ObservationRegistry.NOOP, new SimpleMeterRegistry(), "gateway-test",
                ContextSnapshotFactory.builder().clearMissing(true).build());

        CoreOutboundResult result = dispatcher.executeSynchronously("task callback relay result", request("sync"));

        assertThat(result.httpStatus()).isEqualTo(202);
        assertThat(result.success2xx()).isTrue();
        Captured captured = requests.poll(3, TimeUnit.SECONDS);
        assertThat(captured.traceparent()).isNotBlank();
        assertThat(captured.tracestate()).isEqualTo("vendor=value");
        assertThat(captured.baggage()).isEqualTo("tenant.id=tenant-a");
    }

    @Test
    void shouldReportTimeoutWithoutLosingCompletionCallback() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/core", exchange -> {
            try { Thread.sleep(500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
        dispatcher = new CoreOutboundDispatcher(properties(1, 1), observableTestClient(Duration.ofMillis(50)),
                ObservationRegistry.NOOP, new SimpleMeterRegistry(), "gateway-test",
                ContextSnapshotFactory.builder().clearMissing(true).build());
        AtomicReference<CoreOutboundResult> result = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        dispatcher.submit("directory sync heartbeat", request("timeout"), value -> {
            result.set(value);
            completed.countDown();
        });

        assertThat(completed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get().status()).isEqualTo(CoreOutboundStatus.TIMEOUT);
    }

    @Test
    void shouldRejectWhenBoundedQueueIsFullAndCountCallbackFailure() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/core", exchange -> {
            try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        dispatcher = new CoreOutboundDispatcher(properties(1, 1), observableTestClient(Duration.ofSeconds(4)),
                ObservationRegistry.NOOP, meters, "gateway-test",
                ContextSnapshotFactory.builder().clearMissing(true).build());

        CoreOutboundSubmission first = dispatcher.submit("inbound event forward", request("one"), result -> { throw new IllegalStateException("callback"); });
        while (dispatcher.activeCount() == 0) Thread.onSpinWait();
        CoreOutboundSubmission second = dispatcher.submit("inbound event forward", request("two"), result -> {});
        CoreOutboundSubmission third = dispatcher.submit("inbound event forward", request("three"), result -> {});

        assertThat(first.accepted()).isTrue();
        assertThat(second.accepted()).isTrue();
        assertThat(third.status()).isEqualTo(CoreOutboundStatus.QUEUE_FULL);
        release.countDown();
        for (int i = 0; i < 100 && dispatcher.completedCount() < 2; i++) Thread.sleep(20);
        assertThat(dispatcher.rejectedCount()).isEqualTo(1);
        assertThat(meters.get("opendispatch.core.outbound.rejected").counter().count()).isEqualTo(1);
        assertThat(meters.get("opendispatch.core.outbound.callback.failures").counter().count()).isEqualTo(1);
    }

    private RestClient observableTestClient(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set("traceparent", "00-11111111111111111111111111111111-2222222222222222-01");
                    request.getHeaders().set("tracestate", "vendor=value");
                    request.getHeaders().set("baggage", "tenant.id=tenant-a");
                    return execution.execute(request, body);
                })
                .build();
    }

    private CoreOutboundRequest request(String body) {
        return CoreOutboundRequest.jsonPost(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/core"),
                body, Map.of());
    }

    private CoreOutboundProperties properties(int workers, int queue) {
        CoreOutboundProperties properties = new CoreOutboundProperties();
        properties.setWorkerThreads(workers);
        properties.setQueueCapacity(queue);
        properties.setShutdownWaitMs(1000);
        return properties;
    }

    private record Captured(String traceparent, String tracestate, String baggage, String body) {}
}
