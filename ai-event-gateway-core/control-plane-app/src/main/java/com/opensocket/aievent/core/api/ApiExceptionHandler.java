package com.opensocket.aievent.core.api;

import jakarta.validation.ConstraintViolationException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationDomainException;
import com.opensocket.aievent.core.iam.identity.domain.IdentityDomainException;
import com.opensocket.aievent.core.iam.organization.domain.OrganizationDomainException;
import com.opensocket.aievent.core.iam.rbac.domain.RbacDomainException;


@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MeterRegistry meterRegistry;

    public ApiExceptionHandler() {
        this.meterRegistry = null;
    }

    @Autowired
    public ApiExceptionHandler(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
    }

    @ExceptionHandler(StandardApiException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleStandardApi(StandardApiException ex) {
        return error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationDomainException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleAuthenticationDomain(AuthenticationDomainException ex) {
        return error(ex.reasonCode().name(), ex.getMessage());
    }

    @ExceptionHandler(OrganizationDomainException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleOrganizationDomain(OrganizationDomainException ex) {
        return error(ex.reasonCode(), ex.getMessage());
    }

    @ExceptionHandler(IdentityDomainException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleIdentityDomain(IdentityDomainException ex) {
        return error(ex.reasonCode(), ex.getMessage());
    }

    @ExceptionHandler(RbacDomainException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleRbacDomain(RbacDomainException ex) {
        return error(ex.reasonCode().name(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return error(StandardApiErrorCode.VALIDATION_ERROR, validationMessage(ex));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleBind(BindException ex) {
        return error(StandardApiErrorCode.VALIDATION_ERROR, validationMessage(ex));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return error(StandardApiErrorCode.VALIDATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        return error(mapStatus(ex.getStatusCode()), ex.getReason() == null || ex.getReason().isBlank() ? ex.getMessage() : ex.getReason());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        logApiFailure("api_method_not_allowed", request, ex);
        String supported = ex.getSupportedHttpMethods() == null || ex.getSupportedHttpMethods().isEmpty()
                ? ""
                : " Supported methods: " + ex.getSupportedHttpMethods();
        return error(StandardApiErrorCode.METHOD_NOT_ALLOWED, ex.getMessage() + supported);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        String name = ex.getParameterName();
        if ("tenantId".equalsIgnoreCase(name)) {
            return error(StandardApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantId is required");
        }
        return error(StandardApiErrorCode.BAD_REQUEST, name + " is required");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage();
        if ("AUTH_LOGIN_CHALLENGE_INVALID".equals(message)
                || "AUTH_MFA_INVALID".equals(message)) {
            return error(message, message);
        }
        return error(StandardApiErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        String message = ex.getMessage();
        if (message != null && (message.equals("API_IDEMPOTENCY_CONFLICT") || message.equals("RESOURCE_VERSION_CONFLICT") || message.equals("HANDOFF_CONTEXT_REQUIRED_BEFORE_COMPLETION"))) {
            return error(message, message);
        }
        return error(StandardApiErrorCode.INVALID_STATE, message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        logApiFailure("api_data_integrity_violation", request, ex);
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("uq_agent_runtime_bindings_active_agent")
                || message.contains("agent_runtime_bindings")) {
            return error(StandardApiErrorCode.AGENT_RUNTIME_BINDING_CONFLICT,
                    "An active runtime binding already exists for this Agent. Re-run setup should update the existing binding instead of creating a duplicate.");
        }
        String domainCode = databaseDomainCode(message);
        if (domainCode != null) {
            return error(domainCode, databaseDomainMessage(domainCode));
        }
        if (isUniqueViolation(ex)) {
            return error(StandardApiErrorCode.CONFLICT, "A database uniqueness constraint rejected the request.");
        }
        return error(StandardApiErrorCode.INTERNAL_ERROR,
                "The database operation failed while processing the request. No uniqueness conflict was confirmed.");
    }

    private boolean isUniqueViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
            current = current.getCause();
        }
        return false;
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<StandardApiResponse<Void>> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        logApiFailure("api_database_failure", request, ex);
        String domainCode = databaseDomainCode(ex.getMessage());
        if (domainCode != null) {
            return error(domainCode, databaseDomainMessage(domainCode));
        }
        return error(StandardApiErrorCode.INTERNAL_ERROR, StandardApiErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private String databaseDomainCode(String message) {
        if (message == null || message.isBlank()) return null;
        if (message.contains("uq_departments_current_code") || message.contains("DEPARTMENT_CODE_CONFLICT")) return "DEPARTMENT_CODE_CONFLICT";
        if (message.contains("uq_organization_groups_current_code") || message.contains("GROUP_CODE_CONFLICT")) return "GROUP_CODE_CONFLICT";
        if (message.contains("IDENTITY_LAST_TENANT_ADMIN_PROTECTED")) return "IDENTITY_LAST_TENANT_ADMIN_PROTECTED";
        if (message.contains("DEPARTMENT_DISABLE_BLOCKED")) return "DEPARTMENT_DISABLE_BLOCKED";
        if (message.contains("GROUP_DISABLE_BLOCKED")) return "GROUP_DISABLE_BLOCKED";
        if (message.contains("DEPARTMENT_DELETE_BLOCKED")) return "DEPARTMENT_DELETE_BLOCKED";
        if (message.contains("GROUP_DELETE_BLOCKED")) return "GROUP_DELETE_BLOCKED";
        if (message.contains("ROLE_DELETE_BLOCKED")) return "ROLE_DELETE_BLOCKED";
        if (message.contains("DEPARTMENT_DELETED")) return "DEPARTMENT_DELETED";
        if (message.contains("GROUP_DELETED")) return "GROUP_DELETED";
        if (message.contains("ROLE_DELETED")) return "ROLE_DELETED";
        if (message.contains("AGENT_OWNER_DEPARTMENT_REQUIRED")) return "AGENT_OWNER_DEPARTMENT_REQUIRED";
        if (message.contains("AGENT_RESPONSIBILITY_REQUIRED")) return "AGENT_RESPONSIBILITY_REQUIRED";
        if (message.contains("AGENT_BUSINESS_OWNER_REQUIRED")) return "AGENT_BUSINESS_OWNER_REQUIRED";
        if (message.contains("AGENT_BUSINESS_OWNER_INACTIVE")) return "AGENT_BUSINESS_OWNER_INACTIVE";
        if (message.contains("AGENT_TECHNICAL_STEWARD_INACTIVE")) return "AGENT_TECHNICAL_STEWARD_INACTIVE";
        if (message.contains("TENANT_NOT_REGISTERED")) return "TENANT_NOT_REGISTERED";
        if (message.contains("TENANT_IDENTITY_ALIAS_AMBIGUOUS")) return "TENANT_IDENTITY_ALIAS_AMBIGUOUS";
        return null;
    }

    private String databaseDomainMessage(String code) {
        return switch (code) {
            case "DEPARTMENT_CODE_CONFLICT" -> "An active Department already uses this code. Deleted Departments do not reserve business codes.";
            case "GROUP_CODE_CONFLICT" -> "An active Group already uses this code. Deleted Groups do not reserve business codes.";
            case "IDENTITY_LAST_TENANT_ADMIN_PROTECTED" -> "Assign another active Tenant Administrator before removing this person from the workspace.";
            case "DEPARTMENT_DISABLE_BLOCKED" -> "Reassign governed Source/Dispatch resources and A2A Policies before disabling this Department. People and organization relationships may remain attached while the Department is disabled.";
            case "GROUP_DISABLE_BLOCKED" -> "Reassign governed Source/Dispatch resources and A2A Policies before disabling this Group. People and child Group relationships may remain attached while the Group is disabled.";
            case "DEPARTMENT_DELETE_BLOCKED" -> "Reassign governed Source/Dispatch resources and A2A Policies before retiring this Department. People are preserved, memberships end, child Departments are re-parented, and Department-scoped Role Bindings are revoked automatically.";
            case "GROUP_DELETE_BLOCKED" -> "Reassign governed Source/Dispatch resources and A2A Policies before retiring this Group. People are preserved, memberships end, child Groups are re-parented, and Group-scoped Role Bindings are revoked automatically.";
            case "ROLE_DELETE_BLOCKED" -> "Revoke active assignments before deleting this Responsibility.";
            case "DEPARTMENT_DELETED" -> "Deleted Departments cannot be reactivated.";
            case "GROUP_DELETED" -> "Deleted Groups cannot be reactivated.";
            case "ROLE_DELETED" -> "Deleted Responsibilities cannot be reactivated.";
            case "AGENT_OWNER_DEPARTMENT_REQUIRED" -> "Approved Agents require an owning Department. Assign a valid active Department before approval.";
            case "AGENT_RESPONSIBILITY_REQUIRED" -> "Approved Agents require an Agent-compatible Responsibility such as AGENT_RUNTIME.";
            case "AGENT_BUSINESS_OWNER_REQUIRED" -> "Approved Agents require an accountable Human business owner.";
            case "AGENT_BUSINESS_OWNER_INACTIVE" -> "The Agent business owner must be an active Tenant member and an active member of the Agent owning Department.";
            case "AGENT_TECHNICAL_STEWARD_INACTIVE" -> "The Agent technical steward must be an active Tenant member.";
            case "TENANT_NOT_REGISTERED" -> "The requested Tenant is not registered in canonical IAM. Use an existing Tenant ID/code or create/restore the Tenant through IAM administration.";
            case "TENANT_IDENTITY_ALIAS_AMBIGUOUS" -> "The supplied Tenant ID/code resolves to more than one canonical Tenant. Resolve the Tenant identity collision before retrying.";
            default -> "The requested lifecycle change is blocked by active dependencies.";
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        logApiFailure("api_unhandled_exception", request, ex);
        return error(StandardApiErrorCode.INTERNAL_ERROR, StandardApiErrorCode.INTERNAL_ERROR.defaultMessage());
    }


    private void logApiFailure(String event, HttpServletRequest request, Exception ex) {
        OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current().orElse(null);
        Throwable root = rootCause(ex);
        String method = request == null ? "unknown" : request.getMethod();
        String path = request == null ? "unknown" : request.getRequestURI();
        String query = request == null ? "" : nullToEmpty(request.getQueryString());
        log.error("{} method={} path={} query={} requestId={} correlationId={} tenantId={} operatorId={} clientIp={} exception={} rootException={} rootMessage={}",
                event,
                safe(method),
                safe(path),
                safe(query),
                context == null ? "" : safe(context.requestId()),
                context == null ? "" : safe(context.correlationId()),
                context == null ? "" : safe(context.tenantId()),
                context == null ? "" : safe(context.operatorId()),
                context == null ? "" : safe(context.clientAddress()),
                ex == null ? "" : ex.getClass().getName(),
                root == null ? "" : root.getClass().getName(),
                root == null ? "" : safe(root.getMessage()),
                ex);
    }

    private Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        Throwable previous = null;
        while (current != null && current != previous && current.getCause() != null) {
            previous = current;
            current = current.getCause();
        }
        return current == null ? ex : current;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value) {
        if (value == null) return "";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512) + "...";
    }

    private ResponseEntity<StandardApiResponse<Void>> error(StandardApiErrorCode code, String message) {
        recordEnvelopeError(code.code(), statusFor(code.code()));
        return ResponseEntity.status(statusFor(code.code())).body(StandardApiResponse.error(code, message));
    }

    private ResponseEntity<StandardApiResponse<Void>> error(String code, String message) {
        HttpStatus status = statusFor(code);
        recordEnvelopeError(code, status);
        return ResponseEntity.status(status).body(StandardApiResponse.error(code, message));
    }

    private void recordEnvelopeError(String code, HttpStatus status) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("opendispatch.api.envelope.total")
                .description("OpenDispatch API envelope responses by plane, outcome, code and HTTP status.")
                .tag("plane", "core")
                .tag("outcome", "error")
                .tag("code", normalizeMetricTag(code))
                .tag("http_status", String.valueOf(status == null ? 500 : status.value()))
                .register(meterRegistry)
                .increment();
    }

    private String normalizeMetricTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }

    private HttpStatus statusFor(String code) {
        if (code == null || code.isBlank()) return HttpStatus.INTERNAL_SERVER_ERROR;
        String normalized = code.trim().toUpperCase();
        return switch (normalized) {
            case "BAD_REQUEST", "VALIDATION_ERROR", "TENANT_CONTEXT_REQUIRED", "FLOW_AGENT_PROFILE_NOT_FOUND", "API_IDEMPOTENCY_KEY_REQUIRED", "API_CORRELATION_ID_REQUIRED", "API_EXPECTED_VERSION_INVALID", "API_ACTOR_IDENTITY_REQUIRED", "API_AUDIT_REASON_REQUIRED",
                    "AUTH_PASSWORD_POLICY_VIOLATION", "AUTH_PASSWORD_REUSED", "AUTH_PASSWORD_BREACHED",
                    "AUTH_MFA_INVALID", "AUTH_RECOVERY_CODE_INVALID", "AUTH_RECOVERY_CODE_USED",
                    "ROOT_RECOVERY_GRANT_INVALID",
                    "AGENT_OWNER_DEPARTMENT_REQUIRED", "AGENT_RESPONSIBILITY_REQUIRED", "AGENT_BUSINESS_OWNER_REQUIRED",
                    "AGENT_BUSINESS_OWNER_INACTIVE", "AGENT_TECHNICAL_STEWARD_INACTIVE" -> HttpStatus.BAD_REQUEST;
            case "METHOD_NOT_ALLOWED" -> HttpStatus.METHOD_NOT_ALLOWED;
            case "API_EXPECTED_VERSION_REQUIRED" -> HttpStatus.PRECONDITION_REQUIRED;
            case "UNAUTHORIZED", "AUTH_INVALID_CREDENTIALS", "AUTH_LOGIN_CHALLENGE_INVALID",
                    "AUTH_SESSION_EXPIRED", "AUTH_SESSION_REVOKED", "AUTH_SESSION_STALE_EPOCH",
                    "ROOT_RECOVERY_SESSION_EXPIRED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN", "AUTH_ACCOUNT_LOCKED", "AUTH_ACCOUNT_DISABLED", "AUTH_ACCOUNT_SETUP_REQUIRED",
                    "AUTH_PASSWORD_CHANGE_REQUIRED", "AUTH_MFA_REQUIRED",
                    "AUTH_MFA_ENROLLMENT_REQUIRED", "AUTH_TENANT_MEMBERSHIP_REQUIRED",
                    "AUTH_TENANT_SUSPENDED", "AUTH_REAUTHENTICATION_REQUIRED",
                    "AUTH_REAUTHENTICATION_EXPIRED", "ROOT_OPERATION_NOT_ALLOWED",
                    "AUTH_PERMISSION_DENIED", "AUTH_PERMISSION_UNKNOWN", "AUTH_PERMISSION_DISABLED", "AUTH_SCOPE_UNSUPPORTED",
                    "AUTH_SCOPE_MISMATCH", "AUTH_TENANT_MISMATCH", "AUTH_PRINCIPAL_UNSUPPORTED",
                    "RBAC_ROOT_MFA_REQUIRED", "RBAC_ROOT_STEP_UP_REQUIRED", "RBAC_ROOT_SESSION_TOO_LONG",
                    "RBAC_GRANT_ABOVE_ACTOR_FORBIDDEN", "RBAC_ASSIGNABLE_ROLE_BOUNDARY_VIOLATION",
                    "RBAC_ASSIGNABLE_PERMISSION_BOUNDARY_VIOLATION", "RBAC_SELF_BINDING_FORBIDDEN",
                    "RBAC_SELF_ESCALATION_FORBIDDEN", "RBAC_CRITICAL_APPROVAL_SELF_APPROVAL_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND", "CORE_AGENT_NOT_FOUND", "CORE_TASK_NOT_FOUND", "CORE_INCIDENT_NOT_FOUND",
                    "ROLE_NOT_FOUND", "ROLE_PERMISSION_NOT_FOUND", "ROLE_BINDING_NOT_FOUND", "RBAC_CRITICAL_APPROVAL_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CONFLICT", "RESOURCE_VERSION_CONFLICT", "AGENT_POOL_CODE_CONFLICT", "API_IDEMPOTENCY_IN_PROGRESS", "API_IDEMPOTENCY_CONFLICT", "HANDOFF_CONTEXT_REQUIRED_BEFORE_COMPLETION", "INVALID_STATE", "CORE_TASK_INVALID_TRANSITION", "CORE_CALLBACK_INVALID_TRANSITION",
                    "CORE_DISPATCH_DUPLICATE_REQUEST", "AGENT_RUNTIME_BINDING_CONFLICT",
                    "VERSION_CONFLICT", "ORGANIZATION_VERSION_CONFLICT",
                    "TENANT_MEMBERSHIP_NOT_ASSIGNABLE", "TENANT_MEMBERSHIP_ALREADY_EXISTS", "ACTIVE_TENANT_MEMBERSHIP_REQUIRED",
                    "DEPARTMENT_ID_CONFLICT", "DEPARTMENT_CODE_CONFLICT", "GROUP_ID_CONFLICT", "GROUP_CODE_CONFLICT",
                    "OFFICIAL_MANAGER_TENANT_MEMBERSHIP_REQUIRED",
                    "DEPARTMENT_DISABLE_BLOCKED", "GROUP_DISABLE_BLOCKED",
                    "DEPARTMENT_DELETE_BLOCKED", "GROUP_DELETE_BLOCKED", "ROLE_DELETE_BLOCKED",
                    "DEPARTMENT_DELETED", "GROUP_DELETED", "ROLE_DELETED",
                    "PRIMARY_DEPARTMENT_CONFLICT", "PRIMARY_DEPARTMENT_REPLACEMENT_REQUIRED", "PRIMARY_DEPARTMENT_REPLACEMENT_INVALID",
                    "IDENTITY_USERNAME_CONFLICT", "IDENTITY_EMAIL_CONFLICT", "IDENTITY_VERSION_CONFLICT",
                    "IDENTITY_EXISTING_USER_NOT_ADMITTABLE", "IDENTITY_ATTRIBUTES_CONFLICT",
                    "ROLE_CODE_CONFLICT", "ROLE_DISABLED", "ROLE_SYSTEM_MANAGED", "ROLE_BINDING_EXPIRED",
                    "ROLE_BINDING_SCOPE_INVALID", "ROLE_STATUS_TRANSITION_INVALID", "ROLE_SEPARATION_OF_DUTIES_CONFLICT",
                    "RBAC_CRITICAL_APPROVAL_REQUIRED", "RBAC_CRITICAL_APPROVAL_STATE_INVALID", "RBAC_CRITICAL_APPROVAL_EXPIRED",
                    "RBAC_VERSION_CONFLICT", "IDENTITY_LAST_TENANT_ADMIN_PROTECTED", "IDENTITY_LAST_PLATFORM_ADMIN_PROTECTED",
                    "ROOT_BOOTSTRAP_REQUIRED", "ROOT_BOOTSTRAP_ALREADY_COMPLETED" -> HttpStatus.CONFLICT;
            case "RATE_LIMITED", "AUTH_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "DEPENDENCY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "INTERNAL_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> normalized.startsWith("CORE_") ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private StandardApiErrorCode mapStatus(HttpStatusCode status) {
        int value = status == null ? 500 : status.value();
        return switch (value) {
            case 400 -> StandardApiErrorCode.BAD_REQUEST;
            case 401 -> StandardApiErrorCode.UNAUTHORIZED;
            case 403 -> StandardApiErrorCode.FORBIDDEN;
            case 404 -> StandardApiErrorCode.NOT_FOUND;
            case 405 -> StandardApiErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> StandardApiErrorCode.CONFLICT;
            case 429 -> StandardApiErrorCode.RATE_LIMITED;
            case 504 -> StandardApiErrorCode.TIMEOUT;
            case 502, 503 -> StandardApiErrorCode.DEPENDENCY_UNAVAILABLE;
            default -> value >= 500 ? StandardApiErrorCode.INTERNAL_ERROR : StandardApiErrorCode.BAD_REQUEST;
        };
    }

    private String validationMessage(BindException ex) {
        if (ex.getFieldError() != null) {
            return ex.getFieldError().getField() + ": " + ex.getFieldError().getDefaultMessage();
        }
        if (ex.getGlobalError() != null) {
            return ex.getGlobalError().getDefaultMessage();
        }
        return StandardApiErrorCode.VALIDATION_ERROR.defaultMessage();
    }
}
