#!/usr/bin/env python3
"""Verify P16 on-prem/offline release readiness automation."""
from __future__ import annotations

import stat
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    "scripts/release/admin-ui-runtime-entrypoint.sh",
    "scripts/release/release-preflight.sh",
    "scripts/release/build-release-package.sh",
    "scripts/release/verify-release-package.sh",
    "deploy/docker-compose.release.yml",
    "deploy/env/.env.release.example",
    "ci/github/workflows/opendispatch-release-package.yml",
    "Jenkinsfile.release",
]

EXECUTABLE_FILES = [
    "scripts/release/admin-ui-runtime-entrypoint.sh",
    "scripts/release/release-preflight.sh",
    "scripts/release/build-release-package.sh",
    "scripts/release/verify-release-package.sh",
]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def require_file(relative: str) -> Path:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path


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
        "verify:",
        "python3 scripts/verify/verify-release.py",
        "release-package-offline:",
        "--include-admin-runtime-deps",
    ])
    assert_not_contains(makefile, [
        "verify-release:",
        "verify-p15:",
        "verify-p16:",
        "verify-p17:",
        "release-verify:",
    ])

    entrypoint = require_file("scripts/release/admin-ui-runtime-entrypoint.sh")
    assert_contains(entrypoint, [
        "node_modules-prod.tar.gz",
        "ADMIN_UI_ALLOW_NPM_CI",
        "npm ci --omit=dev --no-audit --no-fund",
        "No bundled production dependencies found",
    ])

    preflight = require_file("scripts/release/release-preflight.sh")
    assert_contains(preflight, [
        "--offline",
        "--strict-secrets",
        "--skip-docker",
        "verify-release-package.sh",
        "node_modules-prod.tar.gz",
        "docker compose",
        "docker image inspect",
        "POSTGRES_PASSWORD",
        "CLUSTER_INTERNAL_TOKEN",
        "AGENT_ONBOARDING_TOKEN",
        "NETTY_MACHINE_ADMIN_TOKEN",
    ])

    build_script = require_file("scripts/release/build-release-package.sh")
    assert_contains(build_script, [
        "INCLUDE_ADMIN_RUNTIME_DEPS",
        "--include-admin-runtime-deps",
        "node_modules-prod.tar.gz",
        "admin_runtime_deps_bundle=",
        "offline_admin_ui_supported=",
        "opendispatch-preflight.sh",
        "opendispatch-admin-ui-runtime-entrypoint.sh",
    ])

    verify_script = require_file("scripts/release/verify-release-package.sh")
    assert_contains(verify_script, [
        "--offline",
        "node_modules-prod.tar.gz",
        "offline_admin_ui_supported=true",
        "opendispatch-admin-ui-runtime-entrypoint.sh",
        "ADMIN_UI_ALLOW_NPM_CI",
    ])

    compose = require_file("deploy/docker-compose.release.yml")
    assert_contains(compose, [
        "./scripts/opendispatch-admin-ui-runtime-entrypoint.sh",
        "ADMIN_UI_ALLOW_NPM_CI: ${ADMIN_UI_ALLOW_NPM_CI:-true}",
        "ADMIN_UI_DEPS_ARCHIVE: /workspace/admin-ui/node_modules-prod.tar.gz",
        "opendispatch-release-admin-ui-node-modules:/workspace/admin-ui/node_modules",
    ])
    assert_not_contains(compose, ["npm ci --omit=dev"])

    env = require_file("deploy/env/.env.release.example")
    assert_contains(env, [
        "ADMIN_UI_ALLOW_NPM_CI=true",
        "--include-admin-runtime-deps",
    ])

    workflow = require_file("ci/github/workflows/opendispatch-release-package.yml")
    assert_contains(workflow, [
        "offline_admin_deps",
        "make verify",
        "release-package-offline",
        "--offline",
    ])

    jenkins = require_file("Jenkinsfile.release")
    assert_contains(jenkins, [
        "OFFLINE_ADMIN_DEPS",
        "make verify",
        "release-package-offline",
        "--offline",
    ])

    # Documentation and delivery-summary markdown files are intentionally skipped.

    print("P16 offline release readiness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
