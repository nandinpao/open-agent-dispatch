package com.opensocket.aievent.core.action.executor.issue;

import java.time.Duration;
import java.util.Map;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterSecretRedactor;
import tools.jackson.databind.ObjectMapper;

public class GitlabIssueVendorExecutor extends AbstractHttpIssueVendorExecutor {
    private final AdapterActionExecutionProperties.Gitlab properties;
    private final Duration timeout;

    public GitlabIssueVendorExecutor(AdapterActionExecutionProperties.Gitlab properties,
                                     String executorName,
                                     ObjectMapper mapper,
                                     Duration timeout) {
        super(executorName, mapper, timeout);
        this.properties = properties;
        this.timeout = AdapterSecretRedactor.safeHttpTimeout(timeout, Duration.ofSeconds(30));
    }

    @Override
    public IssueVendor vendor() { return IssueVendor.GITLAB; }

    public boolean enabled() { return properties != null && properties.isEnabled(); }

    @Override
    public IssueExecutorResponse execute(IssueExecutorRequest request) {
        if (!enabled()) return failed("GitLab executor is disabled");
        if (properties.getBaseUrl().isBlank()) return failed("GITLAB_EXECUTOR_BASE_URL is required");
        if (properties.getPrivateToken().isBlank()) return failed("GITLAB_EXECUTOR_PRIVATE_TOKEN is required");
        if (properties.getProjectId().isBlank()) return failed("GITLAB_EXECUTOR_PROJECT_ID is required");
        try {
            if ("ISSUE_UPDATE_COMMENT".equalsIgnoreCase(request.getActionType())) {
                return updateComment(request);
            }
            return createIssue(request);
        } catch (IllegalArgumentException ex) {
            return failed(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        } catch (Exception ex) {
            return failedRetryable(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        }
    }

    private IssueExecutorResponse createIssue(IssueExecutorRequest request) throws Exception {
        Map<String, Object> body = map("title", title(request), "description", description(request));
        var response = sendJson("POST", apiPath("/projects/" + encodedProjectId() + "/issues"), writeJson(body),
                headersWithIdempotency(request, Map.of("PRIVATE-TOKEN", properties.getPrivateToken())), timeout);
        if (!ok(response)) return httpFailure("GitLab create issue", response.statusCode(), response.body());
        Map<String, Object> parsed;
        try {
            parsed = parseJson(response.body());
        } catch (Exception ex) {
            return failed("GitLab create issue response is not valid JSON: " + (ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage()));
        }
        String iid = parsed.get("iid") == null ? null : String.valueOf(parsed.get("iid"));
        if (iid == null || iid.isBlank()) return failed("GitLab create issue response missing iid");
        String webUrl = parsed.get("web_url") == null ? issueUrl(iid) : String.valueOf(parsed.get("web_url"));
        IssueExecutorResponse result = new IssueExecutorResponse();
        result.setSuccess(true);
        result.setVendor(vendor().name());
        result.setIssueId(iid);
        result.setIssueUrl(webUrl);
        result.setIssueStatus(parsed.get("state") == null ? "opened" : String.valueOf(parsed.get("state")));
        result.setResponseRef("gitlab:" + iid);
        return result;
    }

    private IssueExecutorResponse updateComment(IssueExecutorRequest request) throws Exception {
        String issueId = linkedIssueId(request);
        if (issueId == null || issueId.isBlank()) return failed("linkedIssueId is required for GitLab note sync");
        Map<String, Object> body = map("body", comment(request));
        var response = sendJson("POST", apiPath("/projects/" + encodedProjectId() + "/issues/" + encode(issueId) + "/notes"), writeJson(body),
                headersWithIdempotency(request, Map.of("PRIVATE-TOKEN", properties.getPrivateToken())), timeout);
        if (!ok(response)) return httpFailure("GitLab create issue note", response.statusCode(), response.body());
        Map<String, Object> parsed;
        try {
            parsed = parseJson(response.body());
        } catch (Exception ex) {
            return failed("GitLab create issue note response is not valid JSON: " + (ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage()));
        }
        IssueExecutorResponse result = new IssueExecutorResponse();
        result.setSuccess(true);
        result.setVendor(vendor().name());
        result.setIssueId(issueId);
        result.setIssueUrl(issueUrl(issueId));
        result.setIssueStatus("comment_synced");
        result.setCommentId(parsed.get("id") == null ? null : String.valueOf(parsed.get("id")));
        result.setResponseRef("gitlab-note:" + issueId);
        return result;
    }


    private String encodedProjectId() {
        String projectId = properties.getProjectId();
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("GITLAB_EXECUTOR_PROJECT_ID is required");
        }
        String normalized = projectId.trim();
        if (normalized.contains("%2F") || normalized.contains("%2f")) {
            throw new IllegalArgumentException("GITLAB_EXECUTOR_PROJECT_ID must be a numeric id or raw path like group/project; do not pre-encode '/' as %2F");
        }
        return encode(normalized);
    }

    private String apiPath(String path) {
        String base = properties.getBaseUrl();
        if (base.endsWith("/api/v4") || base.contains("/api/v4/")) return joinUrl(base, path);
        return joinUrl(base, "/api/v4" + path);
    }

    private String issueUrl(String issueId) {
        if (!properties.getIssueUrlTemplate().isBlank()) return properties.getIssueUrlTemplate().replace("{issueId}", issueId);
        return joinUrl(properties.getBaseUrl(), "/-/issues/" + encode(issueId));
    }
}
