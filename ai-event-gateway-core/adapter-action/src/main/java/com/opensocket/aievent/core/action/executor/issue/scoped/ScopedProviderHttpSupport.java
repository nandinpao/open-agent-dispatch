package com.opensocket.aievent.core.action.executor.issue.scoped;

import com.opensocket.aievent.core.integration.identity.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

final class ScopedProviderHttpSupport {
    private static final TypeReference<Map<String,Object>> MAP = new TypeReference<>() {};
    private final HttpClient client;
    private final ObjectMapper json;

    ScopedProviderHttpSupport(ObjectMapper json) {
        this.json = json;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    HttpResponse<String> get(String url, Map<String,String> headers, int timeoutMs) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)))
                .header("Accept", "application/json");
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    Map<String,Object> parse(String body) {
        try {
            return body == null || body.isBlank() ? Map.of() : json.readValue(body, MAP);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    Map<String,Object> requireJsonCollection(HttpResponse<String> response, String provider, String operation, String collectionKey) {
        int status = response.statusCode();
        if (!ok(status)) {
            throw new IllegalStateException(provider + " " + operation + " returned HTTP " + status + providerHttpHint(status));
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException(provider + " " + operation + " returned an empty response. Verify the REST API is enabled and the base URL points to the provider root.");
        }
        Map<String,Object> parsed;
        try {
            parsed = json.readValue(body, MAP);
        } catch (Exception failure) {
            String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
            throw new IllegalStateException(provider + " " + operation + " returned a non-JSON response (Content-Type=" + contentType + "). Verify the REST API is enabled and the base URL is the Redmine root.");
        }
        if (!(parsed.get(collectionKey) instanceof List<?>)) {
            throw new IllegalStateException(provider + " " + operation + " returned JSON without the expected '" + collectionKey + "' collection. Verify provider/API compatibility.");
        }
        return parsed;
    }


    Map<String,Object> requireJsonObject(HttpResponse<String> response, String provider, String operation, String objectKey) {
        int status = response.statusCode();
        if (!ok(status)) {
            throw new IllegalStateException(provider + " " + operation + " returned HTTP " + status + providerHttpHint(status));
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException(provider + " " + operation + " returned an empty response. Verify the REST API is enabled and the base URL points to the provider root.");
        }
        Map<String,Object> parsed;
        try {
            parsed = json.readValue(body, MAP);
        } catch (Exception failure) {
            String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
            throw new IllegalStateException(provider + " " + operation + " returned a non-JSON response (Content-Type=" + contentType + "). Verify the REST API is enabled and the base URL is the Redmine root.");
        }
        if (!(parsed.get(objectKey) instanceof Map<?,?>)) {
            throw new IllegalStateException(provider + " " + operation + " returned JSON without the expected '" + objectKey + "' object. Verify provider/API compatibility.");
        }
        return parsed;
    }

    Map<String,String> authHeaders(IntegrationPrincipal principal, IntegrationCredentialMetadata credential, String secret) {
        var headers = new LinkedHashMap<String,String>();
        switch (credential.authType()) {
            case BASIC_PASSWORD, API_TOKEN -> {
                if (principal.externalPrincipalIdentifier() != null && !principal.externalPrincipalIdentifier().isBlank()) {
                    headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            (principal.externalPrincipalIdentifier() + ":" + secret).getBytes(StandardCharsets.UTF_8)));
                } else {
                    headers.put("Authorization", "Bearer " + secret);
                }
            }
            case PERSONAL_ACCESS_TOKEN, OAUTH_CLIENT_CREDENTIALS, OAUTH_REFRESH_TOKEN ->
                    headers.put("Authorization", "Bearer " + secret);
            case WEBHOOK_HMAC -> throw new IllegalArgumentException(
                    "WEBHOOK_HMAC is inbound-only and cannot authenticate outbound provider HTTP");
        }
        return headers;
    }

    Map<String,String> redmineAuthHeaders(IntegrationPrincipal principal, IntegrationCredentialMetadata credential, String secret) {
        var headers = new LinkedHashMap<String,String>();
        switch (credential.authType()) {
            case API_TOKEN, PERSONAL_ACCESS_TOKEN -> headers.put("X-Redmine-API-Key", secret);
            case BASIC_PASSWORD -> {
                if (principal.externalPrincipalIdentifier() == null || principal.externalPrincipalIdentifier().isBlank()) {
                    throw new IllegalStateException("REDMINE_BASIC_AUTH_LOGIN_REQUIRED: configure the Redmine login for this Service Account.");
                }
                headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        (principal.externalPrincipalIdentifier() + ":" + secret).getBytes(StandardCharsets.UTF_8)));
            }
            case OAUTH_CLIENT_CREDENTIALS, OAUTH_REFRESH_TOKEN -> headers.put("Authorization", "Bearer " + secret);
            case WEBHOOK_HMAC -> throw new IllegalArgumentException(
                    "WEBHOOK_HMAC is inbound-only and cannot authenticate outbound provider HTTP");
        }
        return headers;
    }

    static String join(String base, String path) {
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalized + (path.startsWith("/") ? path : "/" + path);
    }

    static Set<String> headerSet(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    static boolean ok(int status) {
        return status >= 200 && status < 300;
    }

    private static String providerHttpHint(int status) {
        return switch (status) {
            case 401 -> ". Authentication was rejected. Verify the Redmine API key/credential reference.";
            case 403 -> ". The Redmine account is authenticated but is not allowed to access this API resource.";
            case 404 -> ". Verify the Redmine base URL and that REST API access is enabled under Administration > Settings > API.";
            default -> status >= 500
                    ? ". The provider returned a server error; inspect the Redmine server/proxy logs."
                    : ". Verify provider REST API availability and the configured base URL.";
        };
    }
}
