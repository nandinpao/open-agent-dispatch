#!/usr/bin/env python3
"""Verify Phase D production security fail-fast gates."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def require_file(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        fail(f"Missing required file: {path}")
    return p.read_text(encoding="utf-8")


def require_contains(path: str, *needles: str) -> None:
    text = require_file(path)
    for needle in needles:
        if needle not in text:
            fail(f"{path} must contain {needle!r}")


def require_not_contains(path: str, *needles: str) -> None:
    text = require_file(path)
    for needle in needles:
        if needle in text:
            fail(f"{path} must not contain {needle!r}")


def main() -> int:
    env = "deploy/env/.env.release.example"
    compose = "deploy/docker-compose.release.yml"
    preflight = "scripts/release/release-preflight.sh"
    package_verify = "scripts/release/verify-release-package.sh"
    admin_validate = "ai-event-gateway-admin-ui/scripts/validate-runtime-env.mjs"
    release_verify = "scripts/verify/verify-release.py"
    docs = "docs/PHASE_D_PRODUCTION_SECURITY_FAIL_FAST.md"

    require_contains(
        env,
        "POSTGRES_PASSWORD=REPLACE_WITH_STRONG_POSTGRES_PASSWORD",
        "REDIS_PASSWORD=REPLACE_WITH_STRONG_REDIS_PASSWORD",
        "CORE_INTERNAL_SECURITY_ENABLED=true",
        "CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO=false",
        "CORE_INTERNAL_ALLOW_LEGACY_TOKEN_HEADER=false",
        "CORE_INTERNAL_SECURITY_AUDIT_LOG_ENABLED=true",
        "GATEWAY_AGENT_AUTHORIZATION_ENABLED=true",
        "GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED=true",
        "GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED=false",
        "AGENT_AUTH_ENABLED=true",
        "AGENT_WS_HANDSHAKE_AUTH_ENABLED=true",
        "NETTY_MACHINE_AUTH_ENABLED=true",
        "NETTY_MACHINE_WS_HANDSHAKE_AUTH_ENABLED=true",
        "NETTY_MACHINE_ADMIN_TOKEN=REPLACE_WITH_STRONG_NETTY_MACHINE_ADMIN_TOKEN",
        "CORE_ADMIN_AUTH_ENABLED=true",
        "ADMIN_UI_SECURITY_PROFILE=prod",
        "ADMIN_UI_FAIL_CLOSED=true",
        "NEXT_PUBLIC_AUTH_ENABLED=true",
        "CORE_FORWARD_BROWSER_AUTHORIZATION=false",
        "ADMIN_UI_COOKIE_SECURE=true",
        "CORE_GATEWAY_INTERNAL_TOKEN=REPLACE_WITH_STRONG_CORE_GATEWAY_TOKEN",
        "CORE_RECOVERY_APPROVER_INTERNAL_TOKEN=REPLACE_WITH_STRONG_CORE_RECOVERY_APPROVER_TOKEN",
    )
    require_not_contains(
        env,
        "release-cluster-token-change-me",
        "release-agent-onboarding-token-change-me",
        "release-admin-api-token-change-me",
        "NEXT_PUBLIC_AUTH_ENABLED=false",
        "CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO=true",
    )

    require_contains(
        compose,
        "POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}",
        "REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}",
        "--requirepass",
        "SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}",
        "SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}",
        "CORE_GATEWAY_INTERNAL_TOKEN: ${CORE_GATEWAY_INTERNAL_TOKEN:?CORE_GATEWAY_INTERNAL_TOKEN is required}",
        "CORE_RECOVERY_APPROVER_INTERNAL_TOKEN: ${CORE_RECOVERY_APPROVER_INTERNAL_TOKEN:?CORE_RECOVERY_APPROVER_INTERNAL_TOKEN is required}",
        "GATEWAY_CORE_TASK_CALLBACK_RELAY_AUTH_TOKEN: ${GATEWAY_CORE_TASK_CALLBACK_RELAY_AUTH_TOKEN:?GATEWAY_CORE_TASK_CALLBACK_RELAY_AUTH_TOKEN is required}",
        "GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED: ${GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED:-true}",
        "GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED: ${GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED:-false}",
        "AGENT_WS_HANDSHAKE_AUTH_ENABLED: ${AGENT_WS_HANDSHAKE_AUTH_ENABLED:-true}",
        "NETTY_MACHINE_AUTH_ENABLED: ${NETTY_MACHINE_AUTH_ENABLED:-true}",
        "NETTY_MACHINE_WS_HANDSHAKE_AUTH_ENABLED: ${NETTY_MACHINE_WS_HANDSHAKE_AUTH_ENABLED:-true}",
        "NETTY_MACHINE_ADMIN_TOKEN: ${NETTY_MACHINE_ADMIN_TOKEN:?NETTY_MACHINE_ADMIN_TOKEN is required}",
        "CORE_ADMIN_AUTH_ENABLED: ${CORE_ADMIN_AUTH_ENABLED:-true}",
        "ADMIN_UI_SECURITY_PROFILE: ${ADMIN_UI_SECURITY_PROFILE:-prod}",
        "ADMIN_UI_FAIL_CLOSED: ${ADMIN_UI_FAIL_CLOSED:-true}",
        "NEXT_PUBLIC_AUTH_ENABLED: ${NEXT_PUBLIC_AUTH_ENABLED:-true}",
        "CORE_FORWARD_BROWSER_AUTHORIZATION: ${CORE_FORWARD_BROWSER_AUTHORIZATION:-false}",
        "ADMIN_UI_COOKIE_SECURE: ${ADMIN_UI_COOKIE_SECURE:-true}",
    )
    require_not_contains(
        compose,
        "release-cluster-token-change-me",
        "release-agent-onboarding-token-change-me",
        "release-admin-api-token-change-me",
        "NEXT_PUBLIC_AUTH_ENABLED: ${NEXT_PUBLIC_AUTH_ENABLED:-false}",
        "CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO: ${CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO:-true}",
        "REDIS_PASSWORD: \"\"",
    )

    require_contains(
        preflight,
        'STRICT_SECRETS="true"',
        "--allow-placeholder-secrets",
        "check_secrets()",
        "require_secret",
        "require_true_env CORE_INTERNAL_SECURITY_ENABLED",
        "require_false_env GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED",
        "require_true_env NETTY_MACHINE_AUTH_ENABLED",
        "require_true_env NETTY_MACHINE_WS_HANDSHAKE_AUTH_ENABLED",
        "require_true_env CORE_ADMIN_AUTH_ENABLED",
        "require_profile_prod",
        "require_distinct_secrets \"Core internal role\"",
        "ADMIN_UI_COOKIE_SECURE",
    )

    require_contains(
        admin_validate,
        "n.includes('replace-with')",
        "n.includes('replace_with')",
        "n.includes('change_me')",
        "requireTrue('NEXT_PUBLIC_AUTH_ENABLED'",
        "requireFalse('CORE_FORWARD_BROWSER_AUTHORIZATION'",
        "for (const obsolete of",
        "ADMIN_UI_COOKIE_SECURE",
        "requireDistinct('Core operator/recovery'",
    )

    require_contains(
        package_verify,
        "Phase D production security fail-fast gates",
        "REPLACE_WITH_STRONG_REDIS_PASSWORD",
        "NEXT_PUBLIC_AUTH_ENABLED=true",
        "GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED=true",
    )
    require_contains(release_verify, "verify-phase-d-production-security-fail-fast.py")
    require_contains(docs, "Phase D", "fail-closed", "opendispatch-preflight.sh")

    print("Phase D production security fail-fast verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
