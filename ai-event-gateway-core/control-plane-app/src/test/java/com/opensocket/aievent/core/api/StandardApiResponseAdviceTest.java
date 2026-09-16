package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class StandardApiResponseAdviceTest {
    private final StandardApiResponseAdvice advice = new StandardApiResponseAdvice();

    @Test
    void shouldApplyStandardEnvelopeAdviceToCoreAndA2AApiPackages() {
        RestControllerAdvice annotation = StandardApiResponseAdvice.class.getAnnotation(RestControllerAdvice.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.basePackages()).contains(
                "com.opensocket.aievent.core.api",
                "com.opensocket.aievent.core.a2a.api");
    }

    @Test
    void shouldWrapA2AOperationsSuccessBodyInStandardEnvelope() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Object wrapped = advice.beforeBodyWrite(
                Map.of("items", java.util.List.of(), "total", 0),
                null,
                MediaType.APPLICATION_JSON,
                null,
                request("/api/a2a-operations"),
                new ServletServerHttpResponse(servletResponse));

        assertThat(wrapped).isInstanceOf(StandardApiResponse.class);
        StandardApiResponse<?> response = (StandardApiResponse<?>) wrapped;
        assertThat(response.getCode()).isEqualTo(StandardApiCode.OK.code());
        assertThat(response.getData()).isEqualTo(Map.of("items", java.util.List.of(), "total", 0));
    }

    @Test
    void shouldWrapCoreSuccessBodyInStandardEnvelope() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Object wrapped = advice.beforeBodyWrite(
                Map.of("dedupStore", "MEMORY"),
                null,
                MediaType.APPLICATION_JSON,
                null,
                request("/api/core/status"),
                new ServletServerHttpResponse(servletResponse));

        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(wrapped).isInstanceOf(StandardApiResponse.class);
        StandardApiResponse<?> response = (StandardApiResponse<?>) wrapped;
        assertThat(response.getCode()).isEqualTo(StandardApiCode.OK.code());
        assertThat(response.getMessage()).isEqualTo(StandardApiCode.OK.defaultMessage());
        assertThat(response.getData()).isEqualTo(Map.of("dedupStore", "MEMORY"));
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void shouldNotDoubleWrapStandardApiResponseAndShouldPreserveTransportStatus() {
        StandardApiResponse<String> alreadyWrapped = StandardApiResponse.ok("ready");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        servletResponse.setStatus(HttpStatus.ACCEPTED.value());

        Object result = advice.beforeBodyWrite(
                alreadyWrapped,
                null,
                MediaType.APPLICATION_JSON,
                null,
                request("/api/core/status"),
                new ServletServerHttpResponse(servletResponse));

        assertThat(result).isSameAs(alreadyWrapped);
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    }

    @Test
    void shouldExcludeHealthAndActuatorPaths() {
        assertThat(advice.shouldWrap(Map.of("status", "UP"), MediaType.APPLICATION_JSON, request("/actuator/health"))).isFalse();
        assertThat(advice.shouldWrap(Map.of("status", "UP"), MediaType.APPLICATION_JSON, request("/api/core/health"))).isFalse();
        assertThat(advice.shouldWrap(Map.of("error", "not found"), MediaType.APPLICATION_JSON, request("/error"))).isFalse();
    }

    @Test
    void shouldExcludeStreamingAndNonJsonBodies() {
        assertThat(advice.shouldWrap(null, MediaType.APPLICATION_JSON, request("/api/core/status"))).isFalse();
        assertThat(advice.shouldWrap("plain", MediaType.TEXT_PLAIN, request("/api/core/status"))).isFalse();
        assertThat(advice.shouldWrap(new byte[] {1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM, request("/api/core/status"))).isFalse();
        assertThat(advice.shouldWrap(Map.of("ok", true), MediaType.TEXT_PLAIN, request("/api/core/status"))).isFalse();
    }

    private ServletServerHttpRequest request(String path) {
        return new ServletServerHttpRequest(new MockHttpServletRequest("GET", path));
    }
}
