#!/usr/bin/env python3
"""P21 API contract runtime acceptance verification.

This is a static guard for the runtime acceptance harness. The actual runtime
acceptance is executed by scripts/acceptance/api-envelope-runtime-acceptance.mjs
against a started stack.
"""
from __future__ import annotations

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
    acceptance_script = "scripts/acceptance/api-envelope-runtime-acceptance.mjs"
    local_smoke = "scripts/ci/local-smoke.sh"
    makefile = "Makefile"
    admin_test = "ai-event-gateway-admin-ui/tests/api-envelope-contract.test.ts"
    admin_package = "ai-event-gateway-admin-ui/package.json"
    verify_release = "scripts/verify/verify-release.py"

    script = require_contains(acceptance_script, [
        "P21 runtime acceptance test",
        "isStandardEnvelope",
        "assertEnvelope",
        "expectCode('Core success API envelope'",
        "expectCode('Core error API envelope'",
        "expectCode('Netty success API envelope'",
        "expectCode('Netty error API envelope'",
        "Admin UI proxy Core envelope pass-through",
        "Admin UI proxy Netty envelope pass-through",
        "Admin UI proxy Core application error pass-through",
        "Admin UI proxy backend transport error envelope",
        "P21_CORE_ERROR_CODE",
        "CORE_AGENT_NOT_FOUND",
        "GATEWAY_AGENT_NOT_FOUND",
        "INTERNAL_ERROR",
        "P21_RUN_ADMIN_PROXY_BACKEND_ERROR",
        "ADMIN_PROXY_CORE_ERROR",
        "ADMIN_PROXY_CORE_UNAVAILABLE",
        "P21_SKIP_ADMIN_PROXY",
        "parseExpectedStatuses",
        "P21_CORE_ERROR_HTTP_STATUSES",
        "P21_NETTY_ERROR_HTTP_STATUSES",
        "expected HTTP status in",
        "code/message/data/timestamp",
    ])
    if "response.ok" in script:
        fail(f"{acceptance_script} must assert configured HTTP statuses instead of generic response.ok")
    if "expected HTTP 200 but got" in script:
        fail(f"{acceptance_script} must not require HTTP 200 for error envelopes")
    if "status === 404" in script or "status === 401" in script:
        fail(f"{acceptance_script} must not branch on hard-coded legacy HTTP business statuses")

    require_contains(local_smoke, [
        "Running P21 API envelope runtime acceptance",
        "scripts/acceptance/api-envelope-runtime-acceptance.mjs",
        "SKIP_ADMIN_UI_SMOKE=\"${SKIP_ADMIN_UI_SMOKE}\"",
    ])

    require_contains(makefile, [
        "smoke:",
        "./scripts/ci/local-smoke.sh",
        "ci-pr:",
        "ci-release:",
    ])
    require_not_contains(makefile, [
        "api-envelope-acceptance:",
        "make api-envelope-acceptance",
    ])

    # Documentation and delivery summaries are intentionally not verified here.
    # Runtime acceptance is guarded by executable scripts, package tests, and Makefile wiring.

    require_contains(admin_package, [
        "test:api-envelope",
        "tests/api-client-envelope.test.ts tests/api-envelope-contract.test.ts",
    ])

    require_contains(admin_test, [
        "P21 API envelope frontend contract helpers",
        "makeStandardApiEnvelope",
        "isStandardApiEnvelope",
        "standardEnvelopeCode",
        "isUnauthorizedApiCode",
        "isNotFoundOrUnsupportedCode",
        "ADMIN_PROXY_CORE_UNAVAILABLE",
        "CORE_AGENT_NOT_FOUND",
        "GATEWAY_AGENT_NOT_FOUND",
    ])

    require_contains(verify_release, [
        "API contract runtime acceptance harness",
        "verify-p21-api-runtime-acceptance.py",
    ])

    print("P21 API contract runtime acceptance verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
