#!/usr/bin/env python3
"""Verify Docker image baseline for PostgreSQL, Redis, and OpenTelemetry Collector.

OpenDispatch local/runtime/container tests must use the same supported image
families everywhere so Java Testcontainers, compose files, env examples, and
release/CI scripts do not drift apart.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

POSTGRES_IMAGE = "postgres:18-alpine"
REDIS_IMAGE = "redis:8-alpine"
OTEL_COLLECTOR_IMAGE = "otel/opentelemetry-collector-contrib:0.156.0"

FORBIDDEN_PATTERNS = [
    (re.compile(r"postgres:(?:16|17)(?:-alpine)?"), "PostgreSQL must use postgres:18-alpine"),
    (re.compile(r"redis:7(?:-alpine)?"), "Redis must use redis:8-alpine"),
    (re.compile(r"otel/opentelemetry-collector-contrib:(?:latest|0\.(?!156\.0)[0-9.]+)"), "OpenTelemetry Collector must use the pinned 0.156.0 image"),
]

SKIP_DIRS = {
    ".git",
    ".next",
    "node_modules",
    "target",
    "build",
    "dist",
    ".runtime",
}
# Do not scan Markdown/docs: documentation can contain historical examples or
# migration notes and must not block executable CI/CD verification.
SKIP_SUFFIXES = {
    ".zip",
    ".jar",
    ".class",
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".ico",
    ".pdf",
    ".sha256",
    ".md",
    ".markdown",
}

REQUIRED_FILES = {
    "deploy/docker-compose.local.yml": [POSTGRES_IMAGE, REDIS_IMAGE, OTEL_COLLECTOR_IMAGE],
    "deploy/docker-compose.ci.yml": [POSTGRES_IMAGE, REDIS_IMAGE, OTEL_COLLECTOR_IMAGE],
    "deploy/docker-compose.release.yml": [POSTGRES_IMAGE, REDIS_IMAGE],
    "deploy/env/.env.local.example": [f"POSTGRES_IMAGE={POSTGRES_IMAGE}", f"REDIS_IMAGE={REDIS_IMAGE}", f"OTEL_COLLECTOR_IMAGE={OTEL_COLLECTOR_IMAGE}"],
    "deploy/env/.env.local.ci": [f"POSTGRES_IMAGE={POSTGRES_IMAGE}", f"REDIS_IMAGE={REDIS_IMAGE}", f"OTEL_COLLECTOR_IMAGE={OTEL_COLLECTOR_IMAGE}"],
    "deploy/env/.env.release.example": [f"POSTGRES_IMAGE={POSTGRES_IMAGE}", f"REDIS_IMAGE={REDIS_IMAGE}"],
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/CorePostgresRedisBaselineContainerTest.java": [POSTGRES_IMAGE, REDIS_IMAGE],
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/PostgresClaimLeaseConcurrencyContainerTest.java": [POSTGRES_IMAGE],
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/PostgresRepositoryIdempotencyContainerTest.java": [POSTGRES_IMAGE],
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/RedisDedupAtomicContainerTest.java": [REDIS_IMAGE],
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/SharedRedissonDedupAtomicContainerTest.java": [REDIS_IMAGE],
    "ai-event-gateway-core/deploy/docker/docker-compose.i6-e2e.yml": [POSTGRES_IMAGE, REDIS_IMAGE],
    "ai-event-gateway-core/deploy/docker/docker-compose.i7-local-integrated.yml": [POSTGRES_IMAGE, REDIS_IMAGE],
    "ai-event-gateway-core/deploy/docker/docker-compose.i7-owner-routing.yml": [POSTGRES_IMAGE, REDIS_IMAGE],
    "ai-event-gateway-admin-ui/deploy/docker/docker-compose.i6-e2e.yml": [POSTGRES_IMAGE, REDIS_IMAGE],
    "ai-event-gateway-admin-ui/deploy/docker/docker-compose.i7-local-integrated.yml": [POSTGRES_IMAGE, REDIS_IMAGE],
    "ai-event-gateway-admin-ui/deploy/docker/docker-compose.i7-owner-routing.yml": [POSTGRES_IMAGE, REDIS_IMAGE],
    "ai-event-gateway-core/scripts/db/_pg_client.sh": [POSTGRES_IMAGE],
    "ai-event-gateway-admin-ui/scripts/db/_pg_client.sh": [POSTGRES_IMAGE],
}


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def read_text(path: Path) -> str:
    try:
        return path.read_text()
    except UnicodeDecodeError:
        return ""


def should_scan(path: Path) -> bool:
    rel = path.relative_to(ROOT)
    if any(part in SKIP_DIRS for part in rel.parts):
        return False
    if path.suffix in SKIP_SUFFIXES:
        return False
    if rel.as_posix() == "scripts/verify/verify-p22-docker-image-policy.py":
        return False
    return path.is_file()


def verify_no_forbidden_images() -> None:
    violations: list[str] = []
    for path in ROOT.rglob("*"):
        if not should_scan(path):
            continue
        text = read_text(path)
        if not text:
            continue
        rel = path.relative_to(ROOT)
        for pattern, message in FORBIDDEN_PATTERNS:
            for match in pattern.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                violations.append(f"{rel}:{line}: {message}; found {match.group(0)}")
    if violations:
        fail("Forbidden Docker image references found:\n" + "\n".join(violations[:80]))


def verify_required_files() -> None:
    missing: list[str] = []
    for rel, expected_values in REQUIRED_FILES.items():
        path = ROOT / rel
        if not path.is_file():
            missing.append(f"missing required file: {rel}")
            continue
        text = read_text(path)
        for expected in expected_values:
            if expected not in text:
                missing.append(f"{rel}: missing {expected}")
    if missing:
        fail("Docker image policy required checks failed:\n" + "\n".join(missing))


def main() -> int:
    verify_no_forbidden_images()
    verify_required_files()
    print("P22 Docker image policy verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
