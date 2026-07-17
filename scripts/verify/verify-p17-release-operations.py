#!/usr/bin/env python3
"""Verify P17 release operations safety automation."""
from __future__ import annotations

import stat
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    "scripts/release/release-backup.sh",
    "scripts/release/release-restore.sh",
    "scripts/release/release-status.sh",
    "scripts/release/release-upgrade.sh",
    "scripts/release/release-rollback.sh",
    "scripts/release/build-release-package.sh",
    "scripts/release/verify-release-package.sh",
    "Makefile",
    "ci/github/workflows/opendispatch-release-package.yml",
    "Jenkinsfile.release",
]

EXECUTABLE_FILES = [
    "scripts/release/release-backup.sh",
    "scripts/release/release-restore.sh",
    "scripts/release/release-status.sh",
    "scripts/release/release-upgrade.sh",
    "scripts/release/release-rollback.sh",
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
    ])
    assert_not_contains(makefile, [
        "verify-release:",
        "verify-p15:",
        "verify-p16:",
        "verify-p17:",
        "release-verify:",
    ])

    clean = require_file("scripts/ci/local-clean.sh")
    assert_contains(clean, [
        "REMOVE_VOLUMES",
        "down -v --remove-orphans",
        "rm -rf \"${CI_OUTPUT_DIR}\"",
    ])

    backup = require_file("scripts/release/release-backup.sh")
    assert_contains(backup, [
        "pg_dump",
        "-Fc",
        "postgres.dump",
        "backup-manifest.txt",
        "SHA256SUMS",
        "--include-redis",
        "--skip-docker",
        "umask 077",
        "chmod 600",
    ])

    restore = require_file("scripts/release/release-restore.sh")
    assert_contains(restore, [
        "pg_restore",
        "--clean",
        "--if-exists",
        "--yes",
        "--restore-redis",
        "up -d postgres redis",
        "sha256sum -c SHA256SUMS",
        "shasum -a 256 -c SHA256SUMS",
    ])

    status = require_file("scripts/release/release-status.sh")
    assert_contains(status, [
        "release-manifest.txt",
        "docker compose",
        "ps",
    ])

    upgrade = require_file("scripts/release/release-upgrade.sh")
    assert_contains(upgrade, [
        "--current-package",
        "--new-package",
        "opendispatch-preflight.sh",
        "opendispatch-backup.sh",
        "opendispatch-down.sh",
        "opendispatch-up.sh",
        "opendispatch-smoke.sh",
        "--offline",
        "--strict-secrets",
        "--skip-port-check",
        "PORT_PREFLIGHT_ARGS",
    ])

    preflight = require_file("scripts/release/release-preflight.sh")
    assert_contains(preflight, [
        "--skip-port-check",
        "SKIP_PORT_CHECK",
        "Skipping host port availability check.",
    ])

    rollback = require_file("scripts/release/release-rollback.sh")
    assert_contains(rollback, [
        "--target-package",
        "--backup",
        "opendispatch-restore.sh",
        "opendispatch-up.sh",
        "opendispatch-smoke.sh",
        "--restore-redis",
    ])

    build = require_file("scripts/release/build-release-package.sh")
    assert_contains(build, [
        "opendispatch-backup.sh",
        "opendispatch-restore.sh",
        "opendispatch-status.sh",
        "opendispatch-upgrade.sh",
        "opendispatch-rollback.sh",
        "release_operations_scripts_included=true",
        "postgres_backup_format=pg_dump_custom",
        "upgrade_strategy=backup_preflight_skip_port_down_portcheck_up_smoke",
        "rollback_strategy=down_restore_up_smoke",
    ])

    verify_pkg = require_file("scripts/release/verify-release-package.sh")
    assert_contains(verify_pkg, [
        "bin/opendispatch-backup.sh",
        "bin/opendispatch-restore.sh",
        "bin/opendispatch-status.sh",
        "bin/opendispatch-upgrade.sh",
        "bin/opendispatch-rollback.sh",
        "release_operations_scripts_included=true",
        "postgres_backup_format=pg_dump_custom",
    ])

    workflow = require_file("ci/github/workflows/opendispatch-release-package.yml")
    assert_contains(workflow, [
        "make verify",
    ])

    jenkins = require_file("Jenkinsfile.release")
    assert_contains(jenkins, [
        "make verify",
    ])

    # Documentation and delivery-summary markdown files are intentionally skipped.

    print("P17 release operations safety verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
