package com.opensocket.aievent.gateway.netty.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class GatewayApiExceptionHandlerStandardEnvelopeTest {
    private final GatewayApiExceptionHandler handler = new GatewayApiExceptionHandler();

    @Test
    void illegalArgumentShouldReturnHttp200StandardEnvelope() {
        ResponseEntity<GatewayApiResponse<Void>> response = handler.handleIllegalArgument(new IllegalArgumentException("agentId is required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(GatewayApiErrorCode.BAD_REQUEST.code());
        assertThat(response.getBody().message()).isEqualTo("agentId is required");
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void responseStatusExceptionShouldMapToCodeButStillReturnHttp200() {
        ResponseEntity<GatewayApiResponse<Void>> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(GatewayApiErrorCode.NOT_FOUND.code());
        assertThat(response.getBody().message()).isEqualTo("Agent not found");
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void gatewayApiExceptionShouldPreserveExplicitCode() {
        ResponseEntity<GatewayApiResponse<Void>> response = handler.handleGatewayApiException(
                new GatewayApiException(GatewayApiErrorCode.GATEWAY_COMMAND_DELIVERY_FAILED, "Delivery failed."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(GatewayApiErrorCode.GATEWAY_COMMAND_DELIVERY_FAILED.code());
        assertThat(response.getBody().message()).isEqualTo("Delivery failed.");
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
