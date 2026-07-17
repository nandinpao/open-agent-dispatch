package com.opensocket.aievent.gateway.netty.outbound;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable request submitted to the bounded Core outbound dispatcher. */
public record CoreOutboundRequest(
        URI uri,
        String method,
        Map<String, String> headers,
        String body
) {
    public CoreOutboundRequest {
        uri = Objects.requireNonNull(uri, "uri is required");
        method = method == null || method.isBlank() ? "POST" : method.trim().toUpperCase();
        headers = headers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        body = body == null ? "" : body;
    }

    public static CoreOutboundRequest jsonPost(URI uri, String body, Map<String, String> headers) {
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        if (headers != null) {
            requestHeaders.putAll(headers);
        }
        return new CoreOutboundRequest(uri, "POST", requestHeaders, body);
    }
}
