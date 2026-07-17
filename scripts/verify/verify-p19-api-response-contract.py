#!/usr/bin/env python3
"""P19 API response contract verification.

P19-A/B establishes the Core standard error envelope:
  { code, message, data, timestamp }
Business/API errors must be returned with HTTP 200 so Admin UI and Netty can
branch on code instead of transport status during later rollout phases.

P19-C adds Admin UI client dual-format support so the frontend can consume:
  1. code/message/data/timestamp standard envelopes
  2. legacy success/data/error envelopes
  3. existing plain DTO responses

P19-D wraps successful Core management/business API responses with the same
standard envelope via ResponseBodyAdvice.

P19-E applies the same envelope contract to Netty admin APIs and updates
Netty Core clients to unwrap Core standard response envelopes.

P19-F hardens the rollout by removing legacy transport-status dependencies
from Admin UI proxy/smoke paths and by preventing newly introduced accepted /
non-envelope business responses in Core and Netty controllers.

P19-H removes remaining feature-level ResponseStatusException / HttpStatus usage from
Core and Netty business source code. Only the standard exception handlers and tests may
reference transport status classes.

P20 moves Admin UI envelope types into lib/api/envelope.ts. The P19 verifier
accepts that public contract location while retaining P19 behavior checks.

P27.11 treats legacy cluster bootstrap helper scripts as optional compatibility
artifacts. P19 must not fail release verification only because an old local
bootstrap utility was removed or renamed.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def read_required(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        fail(f"Missing required file: {path}")
    return p.read_text(encoding="utf-8")


def read_optional(path: str) -> str | None:
    p = ROOT / path
    if not p.is_file():
        return None
    return p.read_text(encoding="utf-8")


def require_optional_contains(path: str, needles: list[str]) -> str | None:
    text = read_optional(path)
    if text is None:
        return None
    for needle in needles:
        if needle not in text:
            fail(f"{path} does not contain required text: {needle}")
    return text


def require_optional_not_contains(path: str, needles: list[str]) -> str | None:
    text = read_optional(path)
    if text is None:
        return None
    for needle in needles:
        if needle in text:
            fail(f"{path} contains forbidden text: {needle}")
    return text


def require_contains(path: str, needles: list[str]) -> str:
    text = read_required(path)
    for needle in needles:
        if needle not in text:
            fail(f"{path} does not contain required text: {needle}")
    return text


def require_not_contains(path: str, needles: list[str]) -> str:
    text = read_required(path)
    for needle in needles:
        if needle in text:
            fail(f"{path} contains forbidden text: {needle}")
    return text


def main() -> int:
    response_path = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/api/StandardApiResponse.java"
    error_code_path = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/api/StandardApiErrorCode.java"
    exception_path = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/api/StandardApiException.java"
    handler_path = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java"
    old_error_path = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/api/ApiErrorResponse.java"
    test_path = "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/ApiExceptionHandlerStandardEnvelopeTest.java"
    advice_path = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/StandardApiResponseAdvice.java"
    advice_test_path = "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/StandardApiResponseAdviceTest.java"
    admin_client_path = "ai-event-gateway-admin-ui/lib/api/client.ts"
    admin_envelope_path = "ai-event-gateway-admin-ui/lib/api/envelope.ts"
    admin_api_path = "ai-event-gateway-admin-ui/lib/api/adminApi.ts"
    core_admin_api_path = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
    admin_client_test_path = "ai-event-gateway-admin-ui/tests/api-client-envelope.test.ts"
    gateway_response_path = "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/api/GatewayApiResponse.java"
    gateway_error_code_path = "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/api/GatewayApiErrorCode.java"
    gateway_exception_path = "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/api/GatewayApiException.java"
    gateway_advice_path = "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/api/GatewayApiResponseAdvice.java"
    gateway_handler_path = "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/api/GatewayApiExceptionHandler.java"
    gateway_advice_test_path = "ai-event-gateway-netty/admin-api/src/test/java/com/opensocket/aievent/gateway/netty/api/GatewayApiResponseAdviceTest.java"
    gateway_handler_test_path = "ai-event-gateway-netty/admin-api/src/test/java/com/opensocket/aievent/gateway/netty/api/GatewayApiExceptionHandlerStandardEnvelopeTest.java"
    core_authorization_client_path = "ai-event-gateway-netty/gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/authorization/CoreAgentConnectionAuthorizationClient.java"

    response = require_contains(response_path, [
        "class StandardApiResponse<T>",
        "private String code;",
        "private String message;",
        "private T data;",
        "private OffsetDateTime timestamp;",
        "public static <T> StandardApiResponse<T> ok",
        "public static StandardApiResponse<Void> ok()",
        "public static <T> StandardApiResponse<T> error",
        "P19 introduces the standard envelope for normal and error responses",
    ])
    if "httpStatus" in response or "success" in response or "errorCode" in response:
        fail(f"{response_path} must expose only code/message/data/timestamp, not legacy status/success/errorCode fields")

    require_contains(error_code_path, [
        "enum StandardApiErrorCode",
        "BAD_REQUEST",
        "VALIDATION_ERROR",
        "UNAUTHORIZED",
        "FORBIDDEN",
        "NOT_FOUND",
        "CONFLICT",
        "INVALID_STATE",
        "INTERNAL_ERROR",
        "CORE_AGENT_NOT_FOUND",
        "CORE_TASK_INVALID_TRANSITION",
        "CORE_DISPATCH_NO_CANDIDATE",
        "CORE_CALLBACK_INVALID_TRANSITION",
        "CORE_ADAPTER_ACTION_FAILED",
    ])

    require_contains(exception_path, [
        "class StandardApiException extends RuntimeException",
        "private final String code;",
        "public String getCode()",
    ])

    handler = require_contains(handler_path, [
        "@RestControllerAdvice",
        "@ExceptionHandler(StandardApiException.class)",
        "@ExceptionHandler(ResponseStatusException.class)",
        "@ExceptionHandler(MethodArgumentNotValidException.class)",
        "@ExceptionHandler(IllegalArgumentException.class)",
        "@ExceptionHandler(IllegalStateException.class)",
        "@ExceptionHandler(Exception.class)",
        "ResponseEntity<StandardApiResponse<Void>>",
        "ResponseEntity.ok(StandardApiResponse.error",
        "StandardApiErrorCode.VALIDATION_ERROR",
        "StandardApiErrorCode.NOT_FOUND",
    ])
    forbidden_handler_patterns = [
        r"ResponseEntity\.status\s*\(",
        r"new\s+ApiErrorResponse\s*\(",
        r"ResponseEntity<ApiErrorResponse>",
        r"HttpStatus\.BAD_REQUEST",
        r"HttpStatus\.CONFLICT",
        r"HttpStatus\.INTERNAL_SERVER_ERROR",
    ]
    for pattern in forbidden_handler_patterns:
        if re.search(pattern, handler):
            fail(f"{handler_path} contains forbidden legacy HTTP-status error behavior matching: {pattern}")

    require_contains(old_error_path, ["@Deprecated(since = \"P19-A\", forRemoval = false)"])


    advice = require_contains(advice_path, [
        "class StandardApiResponseAdvice implements ResponseBodyAdvice<Object>",
        "@RestControllerAdvice(basePackages = \"com.opensocket.aievent.core.api\")",
        "return StandardApiResponse.ok(body);",
        "response.setStatusCode(HttpStatus.OK);",
        "body == null || body instanceof StandardApiResponse<?>" ,
        "path.startsWith(\"/actuator\")",
        "path.endsWith(\"/health\")",
        "body instanceof Resource",
        "body instanceof StreamingResponseBody",
        "MediaType.APPLICATION_JSON.includes(selectedContentType)",
    ])
    if "StandardApiResponse.error" in advice:
        fail(f"{advice_path} must only wrap successful responses; errors belong to ApiExceptionHandler")

    require_contains(advice_test_path, [
        "shouldWrapCoreSuccessBodyInStandardEnvelope",
        "shouldNotDoubleWrapStandardApiResponseAndShouldNormalizeTransportStatus",
        "servletResponse.setStatus(HttpStatus.ACCEPTED.value())",
        "shouldExcludeHealthAndActuatorPaths",
        "shouldExcludeStreamingAndNonJsonBodies",
        "shouldWrap(null, MediaType.APPLICATION_JSON",
        "getCode()).isEqualTo(StandardApiCode.OK.code())",
        "getData()).isEqualTo(Map.of(\"dedupStore\", \"MEMORY\"))",
    ])

    require_contains(test_path, [
        "illegalArgumentShouldReturnHttp200StandardEnvelope",
        "responseStatusExceptionShouldMapToCodeButStillReturnHttp200",
        "standardApiExceptionShouldPreserveExplicitCode",
        "getStatusCode()).isEqualTo(HttpStatus.OK)",
        "getCode()).isEqualTo(StandardApiErrorCode.BAD_REQUEST.code())",
        "getCode()).isEqualTo(StandardApiErrorCode.NOT_FOUND.code())",
        "getCode()).isEqualTo(StandardApiErrorCode.CORE_TASK_INVALID_TRANSITION.code())",
    ])


    require_contains(admin_envelope_path, [
        "export interface StandardApiEnvelope<T>",
        "code: string;",
        "message: string;",
        "data: T | null;",
        "timestamp: string;",
        "export interface LegacyApiEnvelope<T>",
        "export const STANDARD_SUCCESS_CODE = 'OK';",
        "export function isStandardApiEnvelope",
        "export function isLegacyApiEnvelope",
        "export function standardEnvelopeCode",
        "export function isUnauthorizedApiCode",
        "export function isNotFoundOrUnsupportedCode",
    ])

    admin_client = require_contains(admin_client_path, [
        "from '@/lib/api/envelope';",
        "STANDARD_SUCCESS_CODE",
        "isStandardApiEnvelope",
        "isLegacyApiEnvelope",
        "standardEnvelopeCode",
        "function unwrapResponseInternal<T>(body: unknown, status?: number, requireStandardEnvelope = false): T",
        "body.code !== STANDARD_SUCCESS_CODE",
        "isUnauthorizedApiCode(bodyCode)",
        "export function isNotFoundOrUnsupportedApiError",
    ])
    if "function isApiEnvelope" in admin_client or "interface ApiEnvelope" in admin_client:
        fail(f"{admin_client_path} must split standard and legacy envelopes instead of retaining ambiguous ApiEnvelope")

    require_contains(admin_api_path, [
        "isNotFoundOrUnsupportedApiError",
        "if (isNotFoundOrUnsupportedApiError(error)) return undefined;",
    ])

    require_contains(core_admin_api_path, [
        "isNotFoundOrUnsupportedApiError",
        "if (!isNotFoundOrUnsupportedApiError(error))",
    ])

    require_contains(admin_client_test_path, [
        "P19-C API client dual-format response support",
        "unwraps the new code/message/data/timestamp standard envelope",
        "throws ApiError with code when the new standard envelope reports business failure over HTTP 200",
        "keeps legacy success/data/error envelope compatibility",
        "keeps plain DTO response compatibility",
        "treats standard NOT_FOUND codes as not-found compatibility errors even when HTTP status is 200",
    ])

    require_contains("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/EventIntakeApiSmokeTest.java", [
        "containsKeys(\"code\", \"message\", \"data\", \"timestamp\")",
        "containsEntry(\"code\", \"OK\")",
        "Map<String, Object> firstData = data(first);",
    ])

    require_contains("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/GatewayAgentDirectoryApiSmokeTest.java", [
        "containsKeys(\"code\", \"message\", \"data\", \"timestamp\")",
        "List<Map<String, Object>> agentData = listData(agents);",
    ])

    gateway_response = require_contains(gateway_response_path, [
        "record GatewayApiResponse<T>",
        "String code",
        "String message",
        "T data",
        "OffsetDateTime timestamp",
        "public static <T> GatewayApiResponse<T> ok",
        "public static GatewayApiResponse<Void> ok()",
        "public static <T> GatewayApiResponse<T> error",
        "code/message/data/timestamp",
    ])
    if "httpStatus" in gateway_response or "success" in gateway_response or "errorCode" in gateway_response:
        fail(f"{gateway_response_path} must expose only code/message/data/timestamp, not legacy status/success/errorCode fields")

    require_contains(gateway_error_code_path, [
        "enum GatewayApiErrorCode",
        "BAD_REQUEST",
        "VALIDATION_ERROR",
        "UNAUTHORIZED",
        "FORBIDDEN",
        "NOT_FOUND",
        "CONFLICT",
        "INVALID_STATE",
        "INTERNAL_ERROR",
        "GATEWAY_AGENT_NOT_CONNECTED",
        "GATEWAY_COMMAND_DELIVERY_FAILED",
        "GATEWAY_CLUSTER_PEER_UNAVAILABLE",
        "GATEWAY_CORE_PROXY_FAILED",
        "GATEWAY_RUNTIME_DISCONNECT_FAILED",
    ])

    require_contains(gateway_exception_path, [
        "class GatewayApiException extends RuntimeException",
        "private final String code;",
        "public String getCode()",
    ])

    gateway_advice = require_contains(gateway_advice_path, [
        "class GatewayApiResponseAdvice implements ResponseBodyAdvice<Object>",
        "@RestControllerAdvice(basePackages = \"com.opensocket.aievent.gateway.netty\")",
        "return GatewayApiResponse.ok(body);",
        "response.setStatusCode(HttpStatus.OK);",
        "body == null || body instanceof GatewayApiResponse<?>" ,
        "path.startsWith(\"/actuator\")",
        "path.endsWith(\"/health\")",
        "path.endsWith(\"/stream\")",
        "body instanceof Resource",
        "body instanceof StreamingResponseBody",
        "MediaType.APPLICATION_JSON.includes(selectedContentType)",
    ])
    if "GatewayApiResponse.error" in gateway_advice:
        fail(f"{gateway_advice_path} must only wrap successful responses; errors belong to GatewayApiExceptionHandler")

    gateway_handler = require_contains(gateway_handler_path, [
        "@RestControllerAdvice(basePackages = \"com.opensocket.aievent.gateway.netty\")",
        "@ExceptionHandler(GatewayApiException.class)",
        "@ExceptionHandler(ResponseStatusException.class)",
        "@ExceptionHandler(MethodArgumentNotValidException.class)",
        "@ExceptionHandler(IllegalArgumentException.class)",
        "@ExceptionHandler(IllegalStateException.class)",
        "@ExceptionHandler(Exception.class)",
        "ResponseEntity<GatewayApiResponse<Void>>",
        "ResponseEntity.ok(GatewayApiResponse.error",
        "GatewayApiErrorCode.VALIDATION_ERROR",
        "GatewayApiErrorCode.NOT_FOUND",
    ])
    for pattern in forbidden_handler_patterns:
        if re.search(pattern, gateway_handler):
            fail(f"{gateway_handler_path} contains forbidden legacy HTTP-status error behavior matching: {pattern}")

    require_contains(gateway_advice_test_path, [
        "shouldWrapNettyAdminSuccessBodyInStandardEnvelope",
        "shouldNotDoubleWrapGatewayApiResponseAndShouldNormalizeTransportStatus",
        "servletResponse.setStatus(HttpStatus.ACCEPTED.value())",
        "shouldExcludeHealthAndStreamPaths",
        "shouldExcludeStreamingAndNonJsonBodies",
        "response.code()).isEqualTo(GatewayApiCode.OK.code())",
        "response.data()).isEqualTo(Map.of(\"nodeId\", \"gateway-node-test\"))",
    ])

    require_contains(gateway_handler_test_path, [
        "illegalArgumentShouldReturnHttp200StandardEnvelope",
        "responseStatusExceptionShouldMapToCodeButStillReturnHttp200",
        "gatewayApiExceptionShouldPreserveExplicitCode",
        "getStatusCode()).isEqualTo(HttpStatus.OK)",
        "getBody().code()).isEqualTo(GatewayApiErrorCode.BAD_REQUEST.code())",
        "getBody().code()).isEqualTo(GatewayApiErrorCode.NOT_FOUND.code())",
        "getBody().code()).isEqualTo(GatewayApiErrorCode.GATEWAY_COMMAND_DELIVERY_FAILED.code())",
    ])

    require_contains("ai-event-gateway-netty/admin-api/src/test/java/com/opensocket/aievent/gateway/netty/admin/GatewayStatusControllerTest.java", [
        "jsonPath(\"$.code\", is(\"OK\"))",
        "jsonPath(\"$.message\", is(\"Success\"))",
        "jsonPath(\"$.timestamp\").exists()",
        "jsonPath(\"$.data.nodeId\", is(\"gateway-node-test\"))",
    ])

    require_contains("ai-event-gateway-netty/admin-api/src/test/java/com/opensocket/aievent/gateway/netty/admin/AdminControllerTest.java", [
        "jsonPath(\"$.code\", is(\"OK\"))",
        "jsonPath(\"$.data.gateway.nodeId\", is(\"gateway-node-test\"))",
        "jsonPath(\"$.data.transportQueueSize\", is(0))",
    ])

    require_contains(core_authorization_client_path, [
        "readAuthorizationResponse",
        "isStandardApiEnvelope",
        "root.containsKey(\"code\")",
        "root.containsKey(\"message\")",
        "root.containsKey(\"data\")",
        "root.containsKey(\"timestamp\")",
        "objectMapper.convertValue(data, AgentConnectionAuthorizationResponse.class)",
        "AgentConnectionAuthorizationResponse.deny(agentId, code)",
    ])



    backend_proxy_path = "ai-event-gateway-admin-ui/lib/server/backendProxy.ts"
    admin_e2e_path = "ai-event-gateway-admin-ui/scripts/e2e-smoke.mjs"
    core_e2e_path = "ai-event-gateway-core/scripts/e2e-smoke.mjs"
    agent_detail_hook_path = "ai-event-gateway-admin-ui/hooks/useAgentDetail.ts"
    command_delivery_controller_path = "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/delivery/CommandDeliveryController.java"
    cluster_delivery_controller_path = "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/delivery/routing/ClusterDeliveryRouterController.java"
    authorization_control_controller_path = "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/agent/AgentAuthorizationControlController.java"
    task_callback_controller_path = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskCallbackController.java"
    adapter_worker_controller_path = "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/api/InternalAdapterActionWorkerController.java"
    core_bootstrap_path = "ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js"


    backend_proxy = require_contains(backend_proxy_path, [
        "makeStandardApiEnvelope",
        "type StandardApiEnvelope",
        "function standardEnvelope<T>",
        "function standardEnvelopeResponse<T>",
        "'content-encoding'",
        "ADMIN_PROXY_CORE_UNAVAILABLE",
        "ADMIN_PROXY_NETTY_UNAVAILABLE",
        "ADMIN_PROXY_GATEWAY_UNAVAILABLE",
        "ADMIN_PROXY_CORE_ERROR",
        "return NextResponse.json(standardEnvelope(code, message, data), { status: 200 });",
        "const preserveBackendStatus = plane === 'core' && path[0] === 'admin';",
        "status: preserveBackendStatus ? response.status : 200,",
        "if (!response.ok && !preserveBackendStatus)",
    ])
    if "status: response.status" in backend_proxy:
        fail(f"{backend_proxy_path} may preserve status only through the guarded Core Human Admin branch")

    for path in [
        command_delivery_controller_path,
        cluster_delivery_controller_path,
        authorization_control_controller_path,
        task_callback_controller_path,
    ]:
        text = require_not_contains(path, [
            "ResponseEntity.accepted()",
            "ResponseEntity.status(",
            "ResponseEntity.notFound()",
            "ResponseEntity.noContent()",
        ])
        if "ResponseEntity<" in text:
            fail(f"{path} should return business DTOs and let response advice wrap them")

    require_contains(adapter_worker_controller_path, [
        "StandardApiResponse<AdapterWorkItem>",
        "StandardApiResponse.ok(service.claimNext",
        ".orElse(null)",
    ])
    require_not_contains(adapter_worker_controller_path, [
        "ResponseEntity.noContent()",
        "ResponseEntity::ok",
    ])

    admin_client_hardened = require_contains(admin_client_path, [
        "return error instanceof ApiError && isNotFoundOrUnsupportedCode(error.code)",
        "if ((response.status === 401 || isUnauthorizedApiCode(bodyCode)) && !options.skipAuth)",
        "if (response.status === 403 && requiresCsrf && !csrfRetried)",
        "isNotFoundOrUnsupportedCode",
    ])
    if "error.status === 404" in admin_client_hardened or "error.status === 405" in admin_client_hardened:
        fail(f"{admin_client_path} must not branch not-found business behavior on legacy HTTP status codes")

    require_contains(agent_detail_hook_path, [
        "import { isNotFoundOrUnsupportedApiError } from '@/lib/api/client';",
        "if (isNotFoundOrUnsupportedApiError(error)) return undefined;",
        "if (isNotFoundOrUnsupportedApiError(error)) return [];",
    ])
    require_not_contains(agent_detail_hook_path, [
        "status === 404",
        "status === 405",
    ])

    require_contains(core_admin_api_path, [
        "throw new ApiError(`Core task ${taskId} not found`, 200, undefined, 'CORE_TASK_NOT_FOUND');",
    ])
    require_not_contains(core_admin_api_path, [
        "throw new ApiError(`Core task ${taskId} not found`, 404)",
    ])

    for path in [admin_e2e_path, core_e2e_path]:
        e2e = require_contains(path, [
            "const AUTH_REQUIRED_CODES = new Set",
            "function isStandardEnvelope(value)",
            "const authRequired = AUTH_REQUIRED_CODES.has(code || '')",
            "const ok = response.ok && (!envelope || code === 'OK' || authRequired)",
            "Standard auth-required codes are treated as reachable",
        ])
        if "response.status === 401" in e2e or "response.status === 403" in e2e or "401/403 is treated" in e2e:
            fail(f"{path} must treat auth-required as a standard API code, not an HTTP status")

    # Optional legacy local-cluster helper. It is not part of the production API
    # envelope contract and may be removed from lean source packages, but when it
    # exists it must remain aligned with the standard code/message/data/timestamp
    # envelope semantics.
    require_optional_contains(core_bootstrap_path, [
        "function isStandardEnvelope(value)",
        "if (isStandardEnvelope(payload))",
        "if (payload.code !== 'OK')",
        "error.code = payload.code;",
        "return { payload: payload.data };",
        "error.code === 'NOT_FOUND' || error.code === 'CORE_AGENT_NOT_FOUND'",
    ])
    require_optional_not_contains(core_bootstrap_path, [
        "allowedStatuses",
        "error.status === 404",
        "[200, 404]",
        "response.status, payload",
    ])

    # P4-C removed Netty Human Admin authentication. The former AdminAuthService
    # transport-envelope assertions no longer apply; Core owns Human Authentication
    # and returns explicit browser authentication status codes through its dedicated
    # high-priority exception handler.

    explicit_auth_status_sources = {
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/AdminIdentityExceptionHandler.java",
    }
    for base in ["ai-event-gateway-core", "ai-event-gateway-netty"]:
        for java_file in (ROOT / base).rglob("src/main/java/**/*.java"):
            text = java_file.read_text(encoding="utf-8")
            rel = java_file.relative_to(ROOT).as_posix()
            if rel in explicit_auth_status_sources:
                continue
            if any(forbidden in text for forbidden in [
                "ResponseEntity.accepted()",
                "ResponseEntity.status(",
                "ResponseEntity.notFound()",
                "ResponseEntity.noContent()",
                "ResponseEntity.badRequest()",
            ]):
                fail(f"{rel} contains forbidden legacy business HTTP status response construction")

    allowed_transport_status_sources = {
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/StandardApiResponseAdvice.java",
        "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/api/GatewayApiExceptionHandler.java",
        "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/api/GatewayApiResponseAdvice.java",
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/AdminIdentityExceptionHandler.java",
    }
    forbidden_transport_status_patterns = [
        "ResponseStatusException",
        "HttpStatus.BAD_REQUEST",
        "HttpStatus.NOT_FOUND",
        "HttpStatus.CONFLICT",
        "HttpStatus.INTERNAL_SERVER_ERROR",
        "HttpStatus.ACCEPTED",
        "HttpStatus.CREATED",
        "HttpStatus.UNAUTHORIZED",
        "HttpStatus.FORBIDDEN",
        "HttpStatus.SERVICE_UNAVAILABLE",
    ]
    for base in ["ai-event-gateway-core", "ai-event-gateway-netty"]:
        for java_file in (ROOT / base).rglob("src/main/java/**/*.java"):
            rel = java_file.relative_to(ROOT).as_posix()
            if rel in allowed_transport_status_sources:
                continue
            text = java_file.read_text(encoding="utf-8")
            for forbidden in forbidden_transport_status_patterns:
                if forbidden in text:
                    fail(f"{rel} contains forbidden feature-level transport status dependency: {forbidden}")


    print("P19 API response contract verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
