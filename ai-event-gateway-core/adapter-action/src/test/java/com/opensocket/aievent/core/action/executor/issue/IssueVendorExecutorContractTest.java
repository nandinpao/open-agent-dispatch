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
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutionOutcome;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class IssueVendorExecutorContractTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void redmineCreateIssueShouldSendExpectedPayloadAndParseIssueReference() throws Exception {
        try (RecordingIssueServer server = new RecordingIssueServer(201, "{\"issue\":{\"id\":701}}")) {
            RedmineIssueVendorExecutor executor = new RedmineIssueVendorExecutor(redmine(server.baseUrl()), "redmine-test", mapper, Duration.ofSeconds(3));

            IssueExecutorResponse response = executor.execute(request(AdapterActionType.ISSUE_CREATE, Map.of(
                    "issueTitle", "[HIGH] MES pump alarm",
                    "issueDescription", "Agent diagnosis",
                    "projectId", "MES-OPS")));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getVendor()).isEqualTo("REDMINE");
            assertThat(response.getIssueId()).isEqualTo("701");
            assertThat(response.getResponseRef()).isEqualTo("redmine:701");
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.rawPath()).isEqualTo("/issues.json");
                assertThat(request.header("X-Redmine-API-Key")).isEqualTo("redmine-token");
                assertThat(request.header("X-OpenDispatch-Idempotency-Key")).isEqualTo("idem-contract-1");
                assertThat(request.body()).contains("\"project_id\":\"MES-OPS\"");
                assertThat(request.body()).contains("\"subject\":\"[HIGH] MES pump alarm\"");
                assertThat(request.body()).contains("\"description\":\"Agent diagnosis");
                assertThat(request.body()).contains("OpenDispatch: issue-action-idempotency-key=idem-contract-1");
            });
        }
    }

    @Test
    void redmineUpdateCommentShouldAppendNotesToLinkedIssue() throws Exception {
        try (RecordingIssueServer server = new RecordingIssueServer(200, "")) {
            RedmineIssueVendorExecutor executor = new RedmineIssueVendorExecutor(redmine(server.baseUrl()), "redmine-test", mapper, Duration.ofSeconds(3));

            IssueExecutorResponse response = executor.execute(request(AdapterActionType.ISSUE_UPDATE_COMMENT, Map.of(
                    "linkedIssueId", "REDMINE:701",
                    "issueComment", "Agent completed repair recommendation.")));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getIssueStatus()).isEqualTo("comment_synced");
            assertThat(response.getResponseRef()).isEqualTo("redmine-comment:701");
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("PUT");
                assertThat(request.rawPath()).isEqualTo("/issues/701.json");
                assertThat(request.header("X-OpenDispatch-Idempotency-Key")).isEqualTo("idem-contract-1");
                assertThat(request.body()).contains("\"notes\":\"Agent completed repair recommendation.");
                assertThat(request.body()).contains("OpenDispatch: issue-action-idempotency-key=idem-contract-1");
            });
        }
    }

    @Test
    void gitlabCreateIssueShouldEncodeRawProjectPathExactlyOnce() throws Exception {
        try (RecordingIssueServer server = new RecordingIssueServer(201,
                "{\"iid\":42,\"web_url\":\"https://gitlab.example.com/group/project/-/issues/42\",\"state\":\"opened\"}")) {
            GitlabIssueVendorExecutor executor = new GitlabIssueVendorExecutor(gitlab(server.baseUrl(), "group/project"), "gitlab-test", mapper, Duration.ofSeconds(3));

            IssueExecutorResponse response = executor.execute(request(AdapterActionType.ISSUE_CREATE, Map.of(
                    "issueTitle", "[TASK] ERP PO review",
                    "issueDescription", "Agent result body")));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getVendor()).isEqualTo("GITLAB");
            assertThat(response.getIssueId()).isEqualTo("42");
            assertThat(response.getIssueUrl()).endsWith("/issues/42");
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.rawPath()).isEqualTo("/api/v4/projects/group%2Fproject/issues");
                assertThat(request.header("PRIVATE-TOKEN")).isEqualTo("gitlab-token");
                assertThat(request.header("X-OpenDispatch-Idempotency-Key")).isEqualTo("idem-contract-1");
                assertThat(request.body()).contains("\"title\":\"[TASK] ERP PO review\"");
                assertThat(request.body()).contains("\"description\":\"Agent result body");
                assertThat(request.body()).contains("OpenDispatch: issue-action-idempotency-key=idem-contract-1");
            });
        }
    }

    @Test
    void gitlabUpdateCommentShouldCreateIssueNoteAgainstLinkedIssue() throws Exception {
        try (RecordingIssueServer server = new RecordingIssueServer(201, "{\"id\":9001}")) {
            GitlabIssueVendorExecutor executor = new GitlabIssueVendorExecutor(gitlab(server.baseUrl(), "group/project"), "gitlab-test", mapper, Duration.ofSeconds(3));

            IssueExecutorResponse response = executor.execute(request(AdapterActionType.ISSUE_UPDATE_COMMENT, Map.of(
                    "linkedIssueId", "GITLAB:42",
                    "issueComment", "Agent result history comment")));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getCommentId()).isEqualTo("9001");
            assertThat(response.getResponseRef()).isEqualTo("gitlab-note:42");
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.rawPath()).isEqualTo("/api/v4/projects/group%2Fproject/issues/42/notes");
                assertThat(request.header("X-OpenDispatch-Idempotency-Key")).isEqualTo("idem-contract-1");
                assertThat(request.body()).contains("\"body\":\"Agent result history comment");
                assertThat(request.body()).contains("OpenDispatch: issue-action-idempotency-key=idem-contract-1");
            });
        }
    }

    @Test
    void gitlabShouldRejectPreEncodedProjectPathToPreventDoubleEncoding() throws Exception {
        try (RecordingIssueServer server = new RecordingIssueServer(201, "{\"iid\":42}")) {
            GitlabIssueVendorExecutor executor = new GitlabIssueVendorExecutor(gitlab(server.baseUrl(), "group%2Fproject"), "gitlab-test", mapper, Duration.ofSeconds(3));

            IssueExecutorResponse response = executor.execute(request(AdapterActionType.ISSUE_CREATE, Map.of("issueTitle", "should not send")));

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.isRetryable()).isFalse();
            assertThat(response.getError()).contains("must be a numeric id or raw path");
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void issueExecutorShouldClassifyAuthFailureAsPermanentAndRateLimitAsRetryable() throws Exception {
        try (RecordingIssueServer authServer = new RecordingIssueServer(401, "unauthorized")) {
            AdapterExecutionResult authFailure = issueExecutor(redmineProperties(authServer.baseUrl()))
                    .execute(action(Map.of("vendor", "REDMINE", "issueTitle", "auth failure")));

            assertThat(authFailure.getOutcome()).isEqualTo(AdapterExecutionOutcome.PERMANENT_FAILURE);
            assertThat(authFailure.isRetryable()).isFalse();
            assertThat(authFailure.getError()).contains("401");
        }

        try (RecordingIssueServer rateLimitServer = new RecordingIssueServer(429, "rate limited")) {
            AdapterExecutionResult rateLimit = issueExecutor(redmineProperties(rateLimitServer.baseUrl()))
                    .execute(action(Map.of("vendor", "REDMINE", "issueTitle", "rate limit")));

            assertThat(rateLimit.getOutcome()).isEqualTo(AdapterExecutionOutcome.RETRYABLE_FAILURE);
            assertThat(rateLimit.isRetryable()).isTrue();
            assertThat(rateLimit.getError()).contains("429");
        }
    }

    @Test
    void malformedSuccessfulVendorResponseShouldFailPermanentlyWithClearError() throws Exception {
        try (RecordingIssueServer server = new RecordingIssueServer(201, "{\"issue\":{}}")) {
            RedmineIssueVendorExecutor executor = new RedmineIssueVendorExecutor(redmine(server.baseUrl()), "redmine-test", mapper, Duration.ofSeconds(3));

            IssueExecutorResponse response = executor.execute(request(AdapterActionType.ISSUE_CREATE, Map.of("issueTitle", "malformed response")));

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.isRetryable()).isFalse();
            assertThat(response.getError()).contains("missing issue.id");
        }
    }

    @Test
    void vendorHttpFailureShouldRedactSecretsFromOperatorFacingError() throws Exception {
        try (RecordingIssueServer server = new RecordingIssueServer(500,
                "{\"error\":\"PRIVATE-TOKEN=gitlab-secret-token Authorization: Bearer bearer-secret\"}")) {
            GitlabIssueVendorExecutor executor = new GitlabIssueVendorExecutor(gitlab(server.baseUrl(), "group/project"), "gitlab-test", mapper, Duration.ofSeconds(3));

            IssueExecutorResponse response = executor.execute(request(AdapterActionType.ISSUE_CREATE, Map.of("issueTitle", "server failure")));

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.isRetryable()).isTrue();
            assertThat(response.getError()).contains("500", "[REDACTED]");
            assertThat(response.getError()).doesNotContain("gitlab-secret-token", "bearer-secret");
        }
    }

    private IssueTrackingAdapterActionExecutor issueExecutor(AdapterActionExecutionProperties properties) {
        return new IssueTrackingAdapterActionExecutor(properties, new IssueVendorResolver(properties), mapper);
    }

    private AdapterActionExecutionProperties redmineProperties(String baseUrl) {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        properties.getIssue().setDefaultVendor("REDMINE");
        properties.getIssue().getRedmine().setEnabled(true);
        properties.getIssue().getRedmine().setBaseUrl(baseUrl);
        properties.getIssue().getRedmine().setApiKey("redmine-token");
        properties.getIssue().getRedmine().setProjectId("MES-OPS");
        return properties;
    }

    private AdapterActionExecutionProperties.Redmine redmine(String baseUrl) {
        AdapterActionExecutionProperties.Redmine redmine = new AdapterActionExecutionProperties.Redmine();
        redmine.setEnabled(true);
        redmine.setBaseUrl(baseUrl);
        redmine.setApiKey("redmine-token");
        redmine.setProjectId("MES-OPS");
        return redmine;
    }

    private AdapterActionExecutionProperties.Gitlab gitlab(String baseUrl, String projectId) {
        AdapterActionExecutionProperties.Gitlab gitlab = new AdapterActionExecutionProperties.Gitlab();
        gitlab.setEnabled(true);
        gitlab.setBaseUrl(baseUrl);
        gitlab.setPrivateToken("gitlab-token");
        gitlab.setProjectId(projectId);
        return gitlab;
    }

    private IssueExecutorRequest request(AdapterActionType actionType, Map<String, Object> payload) {
        AdapterAction action = action(payload);
        action.setActionType(actionType);
        return IssueExecutorRequest.from(action, IssueVendor.REDMINE);
    }

    private AdapterAction action(Map<String, Object> payload) {
        AdapterAction action = new AdapterAction();
        action.setActionId("act-contract-1");
        action.setIncidentId("incident-contract-1");
        action.setTaskId("task-contract-1");
        action.setAdapterType(AdapterType.ISSUE_TRACKING);
        action.setActionType(AdapterActionType.ISSUE_CREATE);
        action.setIdempotencyKey("idem-contract-1");
        action.setPayload(payload);
        return action;
    }

    private static final class RecordingIssueServer implements AutoCloseable {
        private final HttpServer server;
        private final List<RecordedRequest> requests = new ArrayList<>();
        private final int status;
        private final String responseBody;

        private RecordingIssueServer(int status, String responseBody) throws IOException {
            this.status = status;
            this.responseBody = responseBody == null ? "" : responseBody;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/", this::handle);
            this.server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private synchronized List<RecordedRequest> requests() {
            return List.copyOf(requests);
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();
            synchronized (this) {
                requests.add(new RecordedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getRawPath(),
                        exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"),
                        exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"),
                        exchange.getRequestHeaders().getFirst("X-OpenDispatch-Idempotency-Key"),
                        new String(body, java.nio.charset.StandardCharsets.UTF_8)));
            }
            byte[] response = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private record RecordedRequest(String method, String rawPath, String redmineApiKey, String gitlabToken, String openDispatchIdempotencyKey, String body) {
        String header(String name) {
            if ("X-Redmine-API-Key".equalsIgnoreCase(name)) return redmineApiKey;
            if ("PRIVATE-TOKEN".equalsIgnoreCase(name)) return gitlabToken;
            if ("X-OpenDispatch-Idempotency-Key".equalsIgnoreCase(name)) return openDispatchIdempotencyKey;
            return null;
        }
    }
}
