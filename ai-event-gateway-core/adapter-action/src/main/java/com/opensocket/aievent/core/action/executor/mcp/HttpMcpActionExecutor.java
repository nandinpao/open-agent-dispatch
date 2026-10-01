package com.opensocket.aievent.core.action.executor.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutor;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.action.executor.AdapterSecretRedactor;
import com.opensocket.aievent.core.action.executor.AdapterExecutorUnavailableException;
import com.opensocket.aievent.core.action.executor.AdapterExecutorRuntimeConfigurationView;

@Component
public class HttpMcpActionExecutor implements AdapterActionExecutor {
    private final AdapterActionExecutionProperties properties;
    private final AdapterExecutorRuntimeConfigurationView runtimeConfiguration;
    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;
    private volatile Duration httpClientTimeout;

    public HttpMcpActionExecutor(AdapterActionExecutionProperties properties, AdapterExecutorRuntimeConfigurationView runtimeConfiguration, ObjectMapper objectMapper) {
        this.properties = properties;
        this.runtimeConfiguration = runtimeConfiguration;
        this.objectMapper = objectMapper;
    }

    @Override public String name() { return runtimeConfiguration.mcpExecutorName(); }
    @Override public boolean supports(AdapterAction action) {
        return runtimeConfiguration.mcpHttpEnabled() && action != null && action.getAdapterType() == AdapterType.MCP;
    }

    @Override
    public AdapterExecutionResult execute(AdapterAction action) {
        String endpoint = runtimeConfiguration.mcpEndpointUrl();
        Duration timeout = AdapterSecretRedactor.safeHttpTimeout(runtimeConfiguration.mcpTimeout(), Duration.ofSeconds(30));
        if (endpoint == null || endpoint.isBlank()) throw new AdapterExecutorUnavailableException("MCP endpoint URL is not configured");
        try {
            String body = objectMapper.writeValueAsString(McpExecutorRequest.from(action));
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(timeout)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            if (!properties.getMcp().getBearerToken().isBlank()) builder.header("Authorization", "Bearer " + properties.getMcp().getBearerToken());
            HttpResponse<String> response = client(timeout).send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300)
                return AdapterExecutionResult.success(name(), "mcp-http-response:" + action.getActionId() + ":" + response.statusCode());
            if (response.statusCode() == 404 || response.statusCode() == 400)
                return AdapterExecutionResult.permanentFailure(name(), "MCP endpoint returned " + response.statusCode() + ": " + AdapterSecretRedactor.redactText(response.body()));
            return AdapterExecutionResult.retryableFailure(name(), "MCP endpoint returned " + response.statusCode() + ": " + AdapterSecretRedactor.redactText(response.body()));
        } catch (java.net.http.HttpTimeoutException ex) {
            return AdapterExecutionResult.timeout(name(), "MCP HTTP timeout: " + AdapterSecretRedactor.redactThrowableMessage(ex));
        } catch (Exception ex) {
            return AdapterExecutionResult.retryableFailure(name(), "MCP HTTP executor failed: " + AdapterSecretRedactor.redactThrowableMessage(ex));
        }
    }

    private HttpClient client(Duration timeout) {
        HttpClient current = httpClient;
        if (current != null && timeout.equals(httpClientTimeout)) return current;
        synchronized (this) {
            if (httpClient == null || !timeout.equals(httpClientTimeout)) {
                httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
                httpClientTimeout = timeout;
            }
            return httpClient;
        }
    }
}
