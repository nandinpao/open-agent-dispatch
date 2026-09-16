package com.opensocket.aievent.core.api;

import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import java.lang.reflect.Method;

/**
 * Wraps successful Core management/business API responses in the P19 standard
 * envelope without forcing every controller method to change its return type at
 * once.
 */
@RestControllerAdvice(basePackages = {
        "com.opensocket.aievent.core.api",
        "com.opensocket.aievent.core.a2a.api"
})
@Order(Ordered.LOWEST_PRECEDENCE)
public class StandardApiResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        applyContractMetadata(body, response);
        if (body instanceof StandardApiResponse<?>) {
            // Preserve the status selected by ResponseEntity/exception handlers.
            // The current API contract forbids hiding BAD_REQUEST/INTERNAL_ERROR envelopes behind HTTP 200.
            return body;
        }
        if (!shouldWrap(body, selectedContentType, request)) {
            return body;
        }
        response.setStatusCode(HttpStatus.OK);
        return StandardApiResponse.ok(body);
    }

    private void applyContractMetadata(Object body, ServerHttpResponse response) {
        OpenDispatchRequestContextHolder.current().ifPresent(context -> response.getHeaders().set("X-Correlation-Id", context.correlationId()));
        if (!response.getHeaders().containsHeader("X-Authorization-Decision-Id")) response.getHeaders().set("X-Authorization-Decision-Id", "not-evaluated");
        String syncStatus = reflected(body, "syncStatus", "getSyncStatus");
        response.getHeaders().set("X-Sync-Status", syncStatus == null ? "NOT_APPLICABLE" : syncStatus);
        String version = reflected(body, "resourceVersion", "getResourceVersion", "version", "getVersion");
        if (version != null && !version.isBlank()) response.getHeaders().setETag("\"" + version.replace("\"", "") + "\"");
        String warnings = reflected(body, "warnings", "getWarnings");
        if (warnings != null && !warnings.isBlank() && !"[]".equals(warnings)) {
            response.getHeaders().add("Warning", "299 OpenDispatch \"" + warnings.replace("\"", "'") + "\"");
        }
    }

    private String reflected(Object body, String... methods) {
        if (body == null) return null;
        Object candidate = body instanceof StandardApiResponse<?> envelope ? envelope.getData() : body;
        if (candidate == null) return null;
        for (String name : methods) {
            try { Method method = candidate.getClass().getMethod(name); Object value = method.invoke(candidate); if (value != null) return String.valueOf(value); }
            catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    boolean shouldWrap(Object body, MediaType selectedContentType, ServerHttpRequest request) {
        if (body == null || body instanceof StandardApiResponse<?>) {
            return false;
        }
        if (isExcludedPath(request)) {
            return false;
        }
        if (isExcludedBodyType(body)) {
            return false;
        }
        return isJsonLike(selectedContentType);
    }

    boolean isExcludedPath(ServerHttpRequest request) {
        if (request == null || request.getURI() == null) {
            return false;
        }
        String path = request.getURI().getPath();
        return path.startsWith("/actuator")
                || path.equals("/health")
                || path.equals("/ready")
                || path.equals("/live")
                || path.equals("/error")
                || path.endsWith("/health")
                || path.endsWith("/readiness")
                || path.endsWith("/liveness");
    }

    boolean isExcludedBodyType(Object body) {
        return body instanceof String
                || body instanceof byte[]
                || body instanceof Resource
                || body instanceof StreamingResponseBody;
    }

    boolean isJsonLike(MediaType selectedContentType) {
        if (selectedContentType == null) {
            return true;
        }
        if (MediaType.APPLICATION_JSON.includes(selectedContentType)) {
            return true;
        }
        String subtype = selectedContentType.getSubtype();
        return subtype != null && subtype.endsWith("+json");
    }
}
