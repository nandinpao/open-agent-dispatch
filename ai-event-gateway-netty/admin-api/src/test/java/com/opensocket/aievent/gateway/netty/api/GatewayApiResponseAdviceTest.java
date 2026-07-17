package com.opensocket.aievent.gateway.netty.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayApiResponseAdviceTest {
    private final GatewayApiResponseAdvice advice = new GatewayApiResponseAdvice();

    @Test
    void shouldWrapNettyAdminSuccessBodyInStandardEnvelope() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Object wrapped = advice.beforeBodyWrite(
                Map.of("nodeId", "gateway-node-test"),
                null,
                MediaType.APPLICATION_JSON,
                null,
                request("/api/admin/status"),
                new ServletServerHttpResponse(servletResponse));

        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(wrapped).isInstanceOf(GatewayApiResponse.class);
        GatewayApiResponse<?> response = (GatewayApiResponse<?>) wrapped;
        assertThat(response.code()).isEqualTo(GatewayApiCode.OK.code());
        assertThat(response.message()).isEqualTo(GatewayApiCode.OK.defaultMessage());
        assertThat(response.data()).isEqualTo(Map.of("nodeId", "gateway-node-test"));
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void shouldNotDoubleWrapGatewayApiResponseAndShouldNormalizeTransportStatus() {
        GatewayApiResponse<String> alreadyWrapped = GatewayApiResponse.ok("ready");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        servletResponse.setStatus(HttpStatus.ACCEPTED.value());

        Object result = advice.beforeBodyWrite(
                alreadyWrapped,
                null,
                MediaType.APPLICATION_JSON,
                null,
                request("/api/admin/status"),
                new ServletServerHttpResponse(servletResponse));

        assertThat(result).isSameAs(alreadyWrapped);
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void shouldExcludeHealthAndStreamPaths() {
        assertThat(advice.shouldWrap(Map.of("status", "UP"), MediaType.APPLICATION_JSON, request("/actuator/health"))).isFalse();
        assertThat(advice.shouldWrap(Map.of("status", "UP"), MediaType.APPLICATION_JSON, request("/api/admin/health"))).isFalse();
        assertThat(advice.shouldWrap(Map.of("status", "UP"), MediaType.APPLICATION_JSON, request("/api/admin/runtime/stream"))).isFalse();
        assertThat(advice.shouldWrap(Map.of("error", "not found"), MediaType.APPLICATION_JSON, request("/error"))).isFalse();
    }

    @Test
    void shouldExcludeStreamingAndNonJsonBodies() {
        assertThat(advice.shouldWrap(null, MediaType.APPLICATION_JSON, request("/api/admin/status"))).isFalse();
        assertThat(advice.shouldWrap("plain", MediaType.TEXT_PLAIN, request("/api/admin/status"))).isFalse();
        assertThat(advice.shouldWrap(new byte[] {1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM, request("/api/admin/status"))).isFalse();
        assertThat(advice.shouldWrap(Map.of("ok", true), MediaType.TEXT_PLAIN, request("/api/admin/status"))).isFalse();
    }

    private ServletServerHttpRequest request(String path) {
        return new ServletServerHttpRequest(new MockHttpServletRequest("GET", path));
    }
}
