package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerStandardEnvelopeTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void illegalArgumentShouldReturnHttp400StandardEnvelope() {
        ResponseEntity<StandardApiResponse<Void>> response = handler.handleIllegalArgument(new IllegalArgumentException("tenantId is required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(StandardApiErrorCode.BAD_REQUEST.code());
        assertThat(response.getBody().getMessage()).isEqualTo("tenantId is required");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void responseStatusExceptionShouldMapToHttpStatusAndCode() {
        ResponseEntity<StandardApiResponse<Void>> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(StandardApiErrorCode.NOT_FOUND.code());
        assertThat(response.getBody().getMessage()).isEqualTo("Agent not found");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void standardApiExceptionShouldPreserveExplicitCode() {
        ResponseEntity<StandardApiResponse<Void>> response = handler.handleStandardApi(
                new StandardApiException(StandardApiErrorCode.CORE_TASK_INVALID_TRANSITION, "Cannot transition task."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(StandardApiErrorCode.CORE_TASK_INVALID_TRANSITION.code());
        assertThat(response.getBody().getMessage()).isEqualTo("Cannot transition task.");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }
}
