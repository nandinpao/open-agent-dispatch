package com.opensocket.aievent.worker;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import com.opensocket.aievent.service.ServiceContractVersions;
import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import com.opensocket.aievent.service.adapter.AdapterWorkerCompletionRequest;
import com.opensocket.aievent.service.adapter.AdapterWorkerFailureRequest;
import com.opensocket.aievent.service.adapter.AdapterWorkerHeartbeatRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Observable HTTP client for the external adapter worker's Core contract. */
@Service
public class CoreAdapterActionClient {

    private final AdapterWorkerProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public CoreAdapterActionClient(
            AdapterWorkerProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("adapterWorkerCoreRestClient") RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public Optional<AdapterWorkItem> claim(String adapterType) {
        String claimPath = "/internal/adapter-actions/claim"
                + "?adapterType=" + encode(adapterType)
                + "&workerId=" + encode(properties.getWorkerId())
                + "&leaseSeconds=" + properties.getLeaseSeconds();
        try {
            CoreResponse response = exchange(HttpMethod.POST, claimPath, null);
            if (response.statusCode() == 204) {
                return Optional.empty();
            }
            if (!isSuccessful(response.statusCode())) {
                throw new IllegalStateException("claim returned HTTP " + response.statusCode());
            }
            return Optional.of(objectMapper.readValue(response.body(), AdapterWorkItem.class));
        }
        catch (Exception exception) {
            throw new IllegalStateException("claim failed", exception);
        }
    }

    public void complete(AdapterWorkItem item, AdapterWorkResult result) {
        AdapterWorkerCompletionRequest request = new AdapterWorkerCompletionRequest(
                properties.getWorkerId(), result.responseRef(), Map.of());
        post("/internal/adapter-actions/" + item.actionId() + "/complete", request);
    }

    public void fail(AdapterWorkItem item, AdapterWorkResult result) {
        AdapterWorkerFailureRequest request = new AdapterWorkerFailureRequest(
                properties.getWorkerId(), result.error(), result.retryable());
        post("/internal/adapter-actions/" + item.actionId() + "/fail", request);
    }

    public void heartbeat(AdapterWorkItem item) {
        AdapterWorkerHeartbeatRequest request = new AdapterWorkerHeartbeatRequest(
                properties.getWorkerId(), properties.getLeaseSeconds());
        post("/internal/adapter-actions/" + item.actionId() + "/heartbeat", request);
    }

    String workerId() { return properties.getWorkerId(); }

    private void post(String path, Object body) {
        try {
            CoreResponse response = exchange(HttpMethod.POST, path, objectMapper.writeValueAsString(body));
            if (!isSuccessful(response.statusCode())) {
                throw new IllegalStateException(path + " returned HTTP " + response.statusCode());
            }
        }
        catch (Exception exception) {
            throw new IllegalStateException("worker callback failed", exception);
        }
    }

    private CoreResponse exchange(HttpMethod method, String path, String body) {
        RestClient.RequestBodySpec request = restClient.method(method)
                .uri(path)
                .header(ServiceContractVersions.CONTRACT_HEADER, ServiceContractVersions.ADAPTER_WORKER_V1);
        if (!properties.getToken().isBlank()) {
            request.header(properties.getTokenHeader(), properties.getToken());
        }
        RestClient.RequestHeadersSpec<?> exchange = body == null
                ? request
                : request.header("Content-Type", "application/json").body(body);
        return exchange.exchange((clientRequest, clientResponse) -> new CoreResponse(
                clientResponse.getStatusCode().value(),
                StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8)));
    }

    private boolean isSuccessful(int statusCode) { return statusCode >= 200 && statusCode < 300; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private record CoreResponse(int statusCode, String body) {}
}
