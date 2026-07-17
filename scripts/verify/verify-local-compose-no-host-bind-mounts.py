#!/usr/bin/env python3
"""Verify local/CI Docker Compose does not use macOS host bind mounts for runtime data.

Docker Desktop can fail while creating bind-mount source paths under /host_mnt/Volumes
when the repository lives on an external volume.  The local and CI compose stacks
therefore must use Docker named volumes for runtime jars, Flyway SQL, diagnostics,
OTel config, logs, and mock-agent e2e scripts.
"""
from __future__ import annotations

import sys
from pathlib import Path

try:
    import yaml
except Exception as exc:  # pragma: no cover - verifier environment guard
    print(f"[FAIL] PyYAML is required to verify compose files: {exc}", file=sys.stderr)
    raise SystemExit(1)

ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILES = [
    ROOT / "deploy/docker-compose.local.yml",
    ROOT / "deploy/docker-compose.ci.yml",
]

REQUIRED_NAMED_MOUNTS = {
    "deploy/docker-compose.local.yml": [
        "opendispatch-core-logs:/logs",
        "opendispatch-netty-logs:/logs",
        "opendispatch-adapter-worker-logs:/logs",
        "opendispatch-admin-ui-logs:/workspace/logs",
        "opendispatch-db-migration-sql:/flyway/sql:ro",
        "opendispatch-db-migration-diagnostics:/flyway/diagnostics:ro",
        "opendispatch-otel-collector-config:/etc/otelcol-contrib/config:ro",
        "opendispatch-mock-agent-e2e:/e2e:ro",
    ],
    "deploy/docker-compose.ci.yml": [
        "opendispatch-ci-core-logs:/logs",
        "opendispatch-ci-netty-logs:/logs",
        "opendispatch-ci-adapter-worker-logs:/logs",
        "opendispatch-ci-admin-ui-logs:/workspace/logs",
        "opendispatch-ci-db-migration:/flyway/sql:ro",
        "opendispatch-ci-db-migration-diagnostics:/flyway/diagnostics:ro",
        "opendispatch-ci-otel-collector-config:/etc/otelcol-contrib/config:ro",
        "opendispatch-ci-mock-agent-e2e:/e2e:ro",
    ],
}

FORBIDDEN_TEXT = [
    "${OPENDISPATCH_LOG_ROOT:-../.local/opendispatch-logs}",
    "${OPENDISPATCH_LOG_ROOT:-../.ci-output/logs}",
    "../ai-event-gateway-core/scripts/e2e:/e2e:ro",
    "./observability/otel-collector-config.yml:",
    "../scripts/db/flyway-migrate-with-diagnostics.sh:",
]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def volume_source(volume: object) -> str | None:
    if isinstance(volume, str):
        return volume.split(":", 1)[0]
    if isinstance(volume, dict):
        source = volume.get("source")
        return str(source) if source is not None else None
    return None


def is_host_like_source(source: str) -> bool:
    return source.startswith((".", "/", "~")) or source.startswith("${")


def main() -> int:
    for compose_path in COMPOSE_FILES:
        rel = compose_path.relative_to(ROOT).as_posix()
        if not compose_path.exists():
            fail(f"Missing compose file: {rel}")
        text = compose_path.read_text(encoding="utf-8")
        for forbidden in FORBIDDEN_TEXT:
            if forbidden in text:
                fail(f"{rel} still contains host bind-mount token: {forbidden}")
        for required in REQUIRED_NAMED_MOUNTS[rel]:
            if required not in text:
                fail(f"{rel} missing required named-volume mount: {required}")
        try:
            data = yaml.safe_load(text)
        except Exception as exc:
            fail(f"{rel} is not valid YAML: {exc}")
        services = data.get("services", {}) if isinstance(data, dict) else {}
        for service_name, service in services.items():
            for volume in service.get("volumes", []) or []:
                source = volume_source(volume)
                if source and is_host_like_source(source):
                    fail(f"{rel}:{service_name} uses host-like bind mount source: {source}")
    print("Local/CI compose host bind-mount guard verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
