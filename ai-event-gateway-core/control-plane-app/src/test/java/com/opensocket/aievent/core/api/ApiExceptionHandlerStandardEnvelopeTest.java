package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import java.sql.SQLException;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationDomainException;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationReasonCode;

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
    @Test
    void authenticationPasswordPolicyViolationShouldReturnHttp400WithStableCode() {
        ResponseEntity<StandardApiResponse<Void>> response = handler.handleAuthenticationDomain(
                new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_PASSWORD_POLICY_VIOLATION,
                        "PASSWORD_UPPERCASE_REQUIRED,PASSWORD_SYMBOL_REQUIRED"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("AUTH_PASSWORD_POLICY_VIOLATION");
        assertThat(response.getBody().getMessage())
                .isEqualTo("PASSWORD_UPPERCASE_REQUIRED,PASSWORD_SYMBOL_REQUIRED");
    }

    @Test
    void invalidCredentialsShouldReturnHttp401WithoutGenericInternalError() {
        ResponseEntity<StandardApiResponse<Void>> response = handler.handleAuthenticationDomain(
                new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_INVALID_CREDENTIALS,
                        "Invalid credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void authenticationRateLimitShouldReturnHttp429() {
        ResponseEntity<StandardApiResponse<Void>> response = handler.handleAuthenticationDomain(
                new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_RATE_LIMITED,
                        "Login rate limit exceeded"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("AUTH_RATE_LIMITED");
    }

    @Test
    void nonUniqueDataIntegrityFailureShouldNotPretendToBeAConflict() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "result mapping failed", new SQLException("Bad value for type long", "22003"));

        ResponseEntity<StandardApiResponse<Void>> response = handler.handleDataIntegrity(
                failure, new MockHttpServletRequest("POST", "/api/admin/access/tenants/t/user-onboarding"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(StandardApiErrorCode.INTERNAL_ERROR.code());
        assertThat(response.getBody().getMessage()).contains("No uniqueness conflict was confirmed");
    }

    @Test
    void sqlState23505ShouldRemainAConflict() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "duplicate", new SQLException("duplicate key", "23505"));

        ResponseEntity<StandardApiResponse<Void>> response = handler.handleDataIntegrity(
                failure, new MockHttpServletRequest("POST", "/api/example"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("uniqueness constraint");
    }

    @Test
    void databaseLastTenantAdminGuardShouldRemainHttp409() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "ERROR: IDENTITY_LAST_TENANT_ADMIN_PROTECTED",
                new SQLException("IDENTITY_LAST_TENANT_ADMIN_PROTECTED", "23514"));

        ResponseEntity<StandardApiResponse<Void>> response = handler.handleDataIntegrity(
                failure, new MockHttpServletRequest("DELETE", "/api/admin/access/tenants/t/users/u"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("IDENTITY_LAST_TENANT_ADMIN_PROTECTED");
        assertThat(response.getBody().getMessage()).contains("Assign another active Tenant Administrator");
    }

}
