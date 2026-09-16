package com.opensocket.aievent.core.action.executor.issue;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.issuetracking.connector.IssueCommentCommand;
import com.opensocket.aievent.core.issuetracking.connector.IssueConnectorOperation;
import com.opensocket.aievent.core.issuetracking.connector.IssueCreateCommand;
import com.opensocket.aievent.core.issuetracking.connector.IssueReadCommand;
import com.opensocket.aievent.core.issuetracking.connector.IssueProviderFailureCode;
import com.opensocket.aievent.core.issuetracking.connector.IssueProviderHealthImpact;
import com.opensocket.aievent.core.issuetracking.connector.IssueUpdateCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RedmineIssueConnectorContractTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void typedReadUsesOnlyCanonicalRedmineIssueEndpoint() throws Exception {
        try (RecordingServer server = new RecordingServer(200, "{\"issue\":{\"id\":701,\"status\":{\"name\":\"Open\"}}}")) {
            var connector = connector(server.baseUrl());
            var result = connector.read(new IssueReadCommand("701"));

            assertThat(result.success()).isTrue();
            assertThat(result.operation()).isEqualTo(IssueConnectorOperation.READ);
            assertThat(result.issueId()).isEqualTo("701");
            assertThat(result.issueStatus()).isEqualTo("Open");
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("GET");
                assertThat(request.rawPath()).isEqualTo("/issues/701.json");
                assertThat(request.redmineApiKey()).isEqualTo("redmine-token");
            });
        }
    }

    @Test
    void typedCreateUsesControlledFieldsAndIdempotency() throws Exception {
        try (RecordingServer server = new RecordingServer(201, "{\"issue\":{\"id\":702}}")) {
            var connector = connector(server.baseUrl());
            var result = connector.create(new IssueCreateCommand(
                    "Pump alarm", "Agent diagnosis", "2", "idem-create-1"));

            assertThat(result.success()).isTrue();
            assertThat(result.operation()).isEqualTo(IssueConnectorOperation.CREATE);
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.rawPath()).isEqualTo("/issues.json");
                assertThat(request.idempotencyKey()).isEqualTo("idem-create-1");
                assertThat(request.body()).contains("\"project_id\":\"MES-OPS\"");
                assertThat(request.body()).contains("\"subject\":\"Pump alarm\"");
                assertThat(request.body()).contains("\"priority_id\":2");
                assertThat(request.body()).doesNotContain("custom_fields");
            });
        }
    }

    @Test
    void typedCommentOnlyAppendsNotes() throws Exception {
        try (RecordingServer server = new RecordingServer(204, "")) {
            var connector = connector(server.baseUrl());
            var result = connector.comment(new IssueCommentCommand("703", "Repair completed", "idem-comment-1"));

            assertThat(result.success()).isTrue();
            assertThat(result.operation()).isEqualTo(IssueConnectorOperation.COMMENT);
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("PUT");
                assertThat(request.rawPath()).isEqualTo("/issues/703.json");
                assertThat(request.idempotencyKey()).isEqualTo("idem-comment-1");
                assertThat(request.body()).contains("\"notes\":\"Repair completed");
                assertThat(request.body()).doesNotContain("status_id", "assigned_to_id");
            });
        }
    }

    @Test
    void typedUpdateUsesOnlyControlledUpdateFields() throws Exception {
        try (RecordingServer server = new RecordingServer(204, "")) {
            var connector = connector(server.baseUrl());
            var result = connector.update(new IssueUpdateCommand(
                    "704", "Updated subject", null, "3", "4", "91", "idem-update-1"));

            assertThat(result.success()).isTrue();
            assertThat(result.operation()).isEqualTo(IssueConnectorOperation.UPDATE);
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("PUT");
                assertThat(request.rawPath()).isEqualTo("/issues/704.json");
                assertThat(request.body()).contains("\"subject\":\"Updated subject\"");
                assertThat(request.body()).contains("\"status_id\":3");
                assertThat(request.body()).contains("\"priority_id\":4");
                assertThat(request.body()).contains("\"assigned_to_id\":91");
                assertThat(request.body()).doesNotContain("method", "path", "headers", "apiKey", "credential");
            });
        }
    }

    @Test
    void providerFailuresExposeStableSemanticsWithoutTurningPermissionDenialsIntoHealthFailures() throws Exception {
        assertFailure(401, IssueProviderFailureCode.ISSUE_PROVIDER_AUTHENTICATION_FAILED, IssueProviderHealthImpact.DEGRADED, false);
        assertFailure(403, IssueProviderFailureCode.ISSUE_PROVIDER_PERMISSION_DENIED, IssueProviderHealthImpact.HEALTHY, false);
        assertFailure(404, IssueProviderFailureCode.ISSUE_PROVIDER_RESOURCE_NOT_FOUND, IssueProviderHealthImpact.HEALTHY, false);
        assertFailure(409, IssueProviderFailureCode.ISSUE_PROVIDER_CONFLICT, IssueProviderHealthImpact.HEALTHY, false);
        assertFailure(422, IssueProviderFailureCode.ISSUE_PROVIDER_VALIDATION_FAILED, IssueProviderHealthImpact.HEALTHY, false);
        assertFailure(429, IssueProviderFailureCode.ISSUE_PROVIDER_RATE_LIMITED, IssueProviderHealthImpact.THROTTLED, true);
        assertFailure(503, IssueProviderFailureCode.ISSUE_PROVIDER_UNAVAILABLE, IssueProviderHealthImpact.DEGRADED, true);
    }

    private void assertFailure(int status, IssueProviderFailureCode code, IssueProviderHealthImpact health, boolean retryable) throws Exception {
        try (RecordingServer server = new RecordingServer(status, "provider response")) {
            var result = connector(server.baseUrl()).read(new IssueReadCommand("701"));
            assertThat(result.success()).isFalse();
            assertThat(result.failureCode()).isEqualTo(code);
            assertThat(result.healthImpact()).isEqualTo(health);
            assertThat(result.retryable()).isEqualTo(retryable);
            assertThat(result.providerStatusCode()).isEqualTo(status);
            assertThat(result.errorMessage()).startsWith(code.name());
        }
    }

    @Test
    void legacyBridgeRejectsGenericProxyFieldsBeforeAnyProviderCall() throws Exception {
        try (RecordingServer server = new RecordingServer(200, "{}")) {
            var connector = connector(server.baseUrl());
            AdapterAction action = new AdapterAction();
            action.setActionId("proxy-attempt");
            action.setActionType(AdapterActionType.ISSUE_UPDATE);
            action.setPayload(Map.of(
                    "issueId", "704",
                    "statusId", "3",
                    "method", "DELETE",
                    "path", "/issues/704.json"));
            IssueExecutorRequest request = IssueExecutorRequest.from(action, IssueVendor.REDMINE);

            IssueExecutorResponse result = connector.execute(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("Generic provider proxy field is not allowed");
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void updateContractRejectsEmptyArbitraryMutation() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new IssueUpdateCommand(
                "704", null, null, null, null, null, "idem"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("controlled Issue update field");
    }

    private RedmineIssueVendorExecutor connector(String baseUrl) {
        AdapterActionExecutionProperties.Redmine props = new AdapterActionExecutionProperties.Redmine();
        props.setEnabled(true);
        props.setBaseUrl(baseUrl);
        props.setApiKey("redmine-token");
        props.setProjectId("MES-OPS");
        return new RedmineIssueVendorExecutor(props, "redmine-i0-b-contract", mapper, Duration.ofSeconds(3));
    }

    private static final class RecordingServer implements AutoCloseable {
        private final HttpServer server;
        private final List<RecordedRequest> requests = new ArrayList<>();
        private final int status;
        private final String responseBody;

        private RecordingServer(int status, String responseBody) throws IOException {
            this.status = status;
            this.responseBody = responseBody == null ? "" : responseBody;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/", this::handle);
            this.server.start();
        }

        private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        private synchronized List<RecordedRequest> requests() { return List.copyOf(requests); }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();
            synchronized (this) {
                requests.add(new RecordedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getRawPath(),
                        exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"),
                        exchange.getRequestHeaders().getFirst("X-OpenDispatch-Idempotency-Key"),
                        new String(body, java.nio.charset.StandardCharsets.UTF_8)));
            }
            byte[] response = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            if (response.length > 0) exchange.getResponseBody().write(response);
            exchange.close();
        }

        @Override public void close() { server.stop(0); }
    }

    private record RecordedRequest(String method, String rawPath, String redmineApiKey, String idempotencyKey, String body) {}
}
