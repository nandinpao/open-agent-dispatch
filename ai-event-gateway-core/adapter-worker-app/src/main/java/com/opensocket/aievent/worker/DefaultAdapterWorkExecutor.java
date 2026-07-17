package com.opensocket.aievent.worker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DefaultAdapterWorkExecutor implements AdapterWorkExecutor {

    private final AdapterWorkerProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DefaultAdapterWorkExecutor(
            AdapterWorkerProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .build();
    }

    @Override
    public boolean supports(AdapterWorkItem item) {
        return item != null;
    }

    @Override
    public AdapterWorkResult execute(AdapterWorkItem item) {
        String endpoint = resolveEndpoint(item.adapterType());
        if (endpoint.isBlank()) {
            return properties.isMockSuccessEnabled()
                    ? AdapterWorkResult.success(
                            "mock:" + properties.getWorkerId() + ":" + item.actionId())
                    : AdapterWorkResult.failure(
                            "No external executor endpoint configured for " + item.adapterType(),
                            true);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(properties.getRequestTimeout())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", resolveIdempotencyKey(item))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(item)))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return AdapterWorkResult.success(
                        item.adapterType().toLowerCase()
                                + ":" + item.actionId()
                                + ":" + statusCode);
            }

            return AdapterWorkResult.failure(
                    item.adapterType()
                            + " endpoint returned " + statusCode
                            + ": " + response.body(),
                    statusCode >= 500 || statusCode == 429);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AdapterWorkResult.failure("Adapter execution interrupted", true);
        } catch (IOException | RuntimeException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getName()
                    : exception.getMessage();
            return AdapterWorkResult.failure(message, true);
        }
    }

    private String resolveEndpoint(String adapterType) {
        if ("MCP".equalsIgnoreCase(adapterType)) {
            return properties.getMcpEndpointUrl();
        }
        if ("ISSUE_TRACKING".equalsIgnoreCase(adapterType)) {
            return properties.getIssueEndpointUrl();
        }
        return "";
    }

    private String resolveIdempotencyKey(AdapterWorkItem item) {
        return item.idempotencyKey() == null || item.idempotencyKey().isBlank()
                ? item.actionId()
                : item.idempotencyKey();
    }
}
