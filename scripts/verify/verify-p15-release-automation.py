#!/usr/bin/env python3
"""Verify P15 external CI/CD and release packaging automation."""
from __future__ import annotations

import stat
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    # Hard gates: runtime/release automation that is required to build and verify
    # an on-prem release package. Do not require documentation or generated
    # dot-directories here; local verification must be code/runtime focused.
    "scripts/release/build-release-package.sh",
    "scripts/release/verify-release-package.sh",
    "scripts/ci/clean-artifacts.sh",
    "scripts/ci/source-clean-check.sh",
    "scripts/release/generate-release-notes.sh",
    "scripts/release/install-ci-workflows.sh",
    "ci/github/workflows/opendispatch-ci.yml",
    "ci/github/workflows/opendispatch-release-package.yml",
    "deploy/docker-compose.release.yml",
    "deploy/env/.env.release.example",
    "Jenkinsfile.release",
]

OPTIONAL_FILES = [
    # Optional delivery material. Validate when present, but do not fail a local
    # runtime/release automation verification only because a documentation file
    # or installed .github workflow is not included in a copied changed-files set.
    ".github/workflows/opendispatch-ci.yml",
    ".github/workflows/opendispatch-release-package.yml",
]

EXECUTABLE_FILES = [
    "scripts/release/build-release-package.sh",
    "scripts/release/verify-release-package.sh",
    "scripts/ci/clean-artifacts.sh",
    "scripts/ci/source-clean-check.sh",
    "scripts/release/generate-release-notes.sh",
    "scripts/release/install-ci-workflows.sh",
]

FORBIDDEN_IMAGE_TOKENS = [
    "opendispatch/core",
    "opendispatch/netty",
    "opendispatch/admin-ui",
    "ai-event-gateway-core:local",
    "ai-event-gateway-netty:local",
    "ai-event-gateway-admin-ui:local",
]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def require_file(relative: str) -> Path:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path


def optional_file(relative: str) -> Path | None:
    path = ROOT / relative
    if path.is_file():
        return path
    return None


def assert_executable(path: Path) -> None:
    if not (path.stat().st_mode & stat.S_IXUSR):
        fail(f"Script is not executable: {path.relative_to(ROOT)}")


def assert_contains(path: Path, needles: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(ROOT)} missing required text: {needle}")


def assert_not_contains(path: Path, needles: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle in text:
            fail(f"{path.relative_to(ROOT)} must not contain: {needle}")


def main() -> int:
    for relative in REQUIRED_FILES:
        require_file(relative)
    for relative in EXECUTABLE_FILES:
        assert_executable(require_file(relative))

    makefile = require_file("Makefile")
    assert_contains(makefile, [
        "release-package:",
        "release-notes:",
        "verify:",
        "python3 scripts/verify/verify-release.py",
        "scripts/release/build-release-package.sh",
        "scripts/release/generate-release-notes.sh",
        "scripts/release/install-ci-workflows.sh",
        "scripts/ci/clean-artifacts.sh",
        "test-source-clean:",
        "install-ci-workflows:",
    ])
    assert_not_contains(makefile, [
        "verify-release:",
        "verify-p15:",
        "verify-p16:",
        "verify-p17:",
        "release-verify:",
    ])

    build_script = require_file("scripts/release/build-release-package.sh")
    assert_contains(build_script, [
        "set -euo pipefail",
        "application_images_built=false",
        "runtime/core/ai-event-gateway-core.jar",
        "runtime/netty/ai-event-gateway-netty.jar",
        "runtime/admin-ui/.next",
        "deploy/docker-compose.release.yml",
        "deploy/env/.env.release.example",
        "sha256sum",
        "shasum -a 256",
        "${RELEASE_NAME}.tar.gz",
        "opendispatch-smoke.sh",
        "--self-check",
        "Source tree cleanliness gate",
        "scripts/ci/source-clean-check.sh",
        "Sanitize staged release metadata",
    ])
    assert_not_contains(build_script, ["docker build"])

    verify_script = require_file("scripts/release/verify-release-package.sh")
    assert_contains(verify_script, [
        "application_images_built=false",
        "runtime/admin-ui/.next/BUILD_ID",
        "validate_jar_like_zip",
        "bash -n",
        "opendispatch-smoke.sh",
        "--self-check",
        "PG_SINGLE_URL",
        "GATEWAY_CORE_DIRECTORY_SYNC_BASE_URL",
        "Release package verification passed",
        "assert_package_tree_clean",
        "Release package contains OS/archive metadata or generated cache files",
    ])

    release_compose = require_file("deploy/docker-compose.release.yml")
    assert_contains(release_compose, [
        "eclipse-temurin:25-jre",
        "node:22-bookworm-slim",
        "postgres:18-alpine",
        "redis:8-alpine",
        "core-db-migrate",
        "service_completed_successfully",
        "../runtime/core/ai-event-gateway-core.jar:/app/ai-event-gateway-core.jar:ro",
        "../runtime/netty/ai-event-gateway-netty.jar:/app/ai-event-gateway-netty.jar:ro",
        "../runtime/admin-ui:/workspace/admin-ui:ro",
        "opendispatch-release-admin-ui-node-modules:/workspace/admin-ui/node_modules",
        "CORE_BACKEND_ORIGIN: http://core:18080",
        "NETTY_BACKEND_ORIGIN: http://netty:18081",
        "DATABASE_PLATFORM_REQUIRE_FLYWAY: \"false\"",
        "SPRING_PROFILES_ACTIVE: ${CORE_PROFILES:-prod}",
        "PG_SINGLE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-ai_event_gateway_release}",
        "PG_SINGLE_USERNAME: ${POSTGRES_USER:-ai_event_release}",
        "PG_SINGLE_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}",
        "PG_MYBATIS_ENABLED: \"true\"",
        "REDIS_ENABLED: \"true\"",
        "REDIS_MODE: single",
        "GATEWAY_CORE_FORWARD_BASE_URL: http://core:18080",
        "GATEWAY_CORE_DIRECTORY_SYNC_BASE_URL: http://core:18080",
        "GATEWAY_CORE_TASK_CALLBACK_RELAY_BASE_URL: http://core:18080",
        "GATEWAY_AGENT_AUTHORIZATION_BASE_URL: http://core:18080",
        "/var/lib/postgresql",
    ])
    assert_not_contains(release_compose, ["build:", "/var/lib/postgresql/data", "CORE_BASE_URL:", *FORBIDDEN_IMAGE_TOKENS])

    release_env = require_file("deploy/env/.env.release.example")
    assert_contains(release_env, [
        "JAVA25_RUNTIME_IMAGE=eclipse-temurin:25-jre",
        "NODE_RUNTIME_IMAGE=node:22-bookworm-slim",
        "POSTGRES_IMAGE=postgres:18-alpine",
        "REDIS_IMAGE=redis:8-alpine",
        "CORE_PROFILES=prod",
        "NETTY_PROFILES=prod",
        "CORE_INTERNAL_SECURITY_ENABLED=true",
        "CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO=false",
        "CORE_INTERNAL_ALLOW_LEGACY_TOKEN_HEADER=false",
        "GATEWAY_CORE_DIRECTORY_SYNC_ENABLED=true",
        "GATEWAY_AGENT_AUTHORIZATION_ENABLED=true",
        "GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED=true",
        "GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED=false",
        "AGENT_AUTH_ENABLED=true",
        "ADMIN_AUTH_ENABLED=true",
        "REDIS_PASSWORD=REPLACE_WITH_STRONG_REDIS_PASSWORD",
        "CLUSTER_INTERNAL_TOKEN=REPLACE_WITH_STRONG_CLUSTER_INTERNAL_TOKEN",
        "NEXT_PUBLIC_APP_NAME=\"AI Event Gateway Admin\"",
    ])

    install_script = require_file("scripts/release/install-ci-workflows.sh")
    assert_contains(install_script, [
        "ci/github/workflows",
        ".github/workflows",
        "opendispatch-ci.yml",
        "opendispatch-release-package.yml",
    ])

    template_ci = require_file("ci/github/workflows/opendispatch-ci.yml")
    template_release = require_file("ci/github/workflows/opendispatch-release-package.yml")
    assert_contains(template_ci, ["make ci-pr", "java-version: '25'", "node-version: '22'"])
    assert_not_contains(template_ci, ["make ci-fast", "make test-admin-strict", "make test-source-clean", "make test-repository-db", "make test-runtime-smoke", "make test-runtime-lifecycle", "make api-envelope-acceptance", "make test-contract"])
    assert_contains(template_release, ["make verify", "make ci-release", "make release-package", "verify-release-package.sh", "upload-artifact"])
    assert_not_contains(template_ci, ["make verify-release", "make release-verify", "make verify-p15", "make verify-p16", "make verify-p17"])
    assert_not_contains(template_release, ["make verify-release", "make release-verify", "make verify-p15", "make verify-p16", "make verify-p17"])

    github_ci = optional_file(".github/workflows/opendispatch-ci.yml")
    if github_ci is not None:
        assert_contains(github_ci, ["make ci-pr", "java-version: '25'", "node-version: '22'"])
        assert_not_contains(github_ci, ["make verify-release", "make release-verify", "make verify-p15", "make verify-p16", "make verify-p17", "make ci-fast", "make test-admin-strict", "make test-source-clean", "make test-repository-db", "make test-runtime-smoke", "make test-runtime-lifecycle", "make api-envelope-acceptance", "make test-contract"])

    github_release = optional_file(".github/workflows/opendispatch-release-package.yml")
    if github_release is not None:
        assert_contains(github_release, ["make verify", "make ci-release", "make release-package", "verify-release-package.sh", "make release-notes", "upload-artifact"])
        assert_not_contains(github_release, ["make verify-release", "make release-verify", "make verify-p15", "make verify-p16", "make verify-p17"])

    jenkins = require_file("Jenkinsfile.release")
    assert_contains(jenkins, ["make verify", "make ci-release", "make release-package", "verify-release-package.sh", "archiveArtifacts"])
    assert_not_contains(jenkins, ["make verify-release", "make release-verify", "make verify-p15", "make verify-p16", "make verify-p17"])

    # Documentation and delivery-summary markdown files are intentionally skipped.

    print("P15 release automation verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
