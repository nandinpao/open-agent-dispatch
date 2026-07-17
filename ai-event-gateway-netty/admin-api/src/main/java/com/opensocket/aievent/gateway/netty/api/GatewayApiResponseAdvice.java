package com.opensocket.aievent.gateway.netty.api;

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

/** Wraps successful Netty admin API responses in the P19 standard envelope. */
@RestControllerAdvice(basePackages = "com.opensocket.aievent.gateway.netty")
@Order(Ordered.LOWEST_PRECEDENCE)
public class GatewayApiResponseAdvice implements ResponseBodyAdvice<Object> {
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
        if (body instanceof GatewayApiResponse<?>) {
            response.setStatusCode(HttpStatus.OK);
            return body;
        }
        if (!shouldWrap(body, selectedContentType, request)) {
            return body;
        }
        response.setStatusCode(HttpStatus.OK);
        return GatewayApiResponse.ok(body);
    }

    boolean shouldWrap(Object body, MediaType selectedContentType, ServerHttpRequest request) {
        if (body == null || body instanceof GatewayApiResponse<?>) {
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
                || path.endsWith("/liveness")
                || path.endsWith("/stream");
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
