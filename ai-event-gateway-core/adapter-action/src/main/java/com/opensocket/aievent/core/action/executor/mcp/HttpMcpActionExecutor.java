package com.opensocket.aievent.core.action.executor.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutor;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.action.executor.AdapterSecretRedactor;
import com.opensocket.aievent.core.action.executor.AdapterExecutorUnavailableException;

@Component
@ConditionalOnProperty(prefix = "adapter-executor.mcp", name = "http-enabled", havingValue = "true")
public class HttpMcpActionExecutor implements AdapterActionExecutor {
    private final AdapterActionExecutionProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpMcpActionExecutor(AdapterActionExecutionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(AdapterSecretRedactor.safeHttpTimeout(properties.getMcp().getTimeout(), java.time.Duration.ofSeconds(30)))
                .build();
    }

    @Override
    public String name() {
        return properties.getMcp().getExecutorName();
    }

    @Override
    public boolean supports(AdapterAction action) {
        return action != null && action.getAdapterType() == AdapterType.MCP;
    }

    @Override
    public AdapterExecutionResult execute(AdapterAction action) {
        if (properties.getMcp().getEndpointUrl() == null || properties.getMcp().getEndpointUrl().isBlank()) {
            throw new AdapterExecutorUnavailableException("MCP endpoint URL is not configured");
        }
        try {
            String body = objectMapper.writeValueAsString(McpExecutorRequest.from(action));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getMcp().getEndpointUrl()))
                    .timeout(AdapterSecretRedactor.safeHttpTimeout(properties.getMcp().getTimeout(), java.time.Duration.ofSeconds(30)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!properties.getMcp().getBearerToken().isBlank()) {
                builder.header("Authorization", "Bearer " + properties.getMcp().getBearerToken());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return AdapterExecutionResult.success(name(), "mcp-http-response:" + action.getActionId() + ":" + response.statusCode());
            }
            if (response.statusCode() == 404 || response.statusCode() == 400) {
                return AdapterExecutionResult.permanentFailure(name(), "MCP endpoint returned " + response.statusCode() + ": " + AdapterSecretRedactor.redactText(response.body()));
            }
            return AdapterExecutionResult.retryableFailure(name(), "MCP endpoint returned " + response.statusCode() + ": " + AdapterSecretRedactor.redactText(response.body()));
        } catch (java.net.http.HttpTimeoutException ex) {
            return AdapterExecutionResult.timeout(name(), "MCP HTTP timeout: " + AdapterSecretRedactor.redactThrowableMessage(ex));
        } catch (Exception ex) {
            return AdapterExecutionResult.retryableFailure(name(), "MCP HTTP executor failed: " + AdapterSecretRedactor.redactThrowableMessage(ex));
        }
    }
}
