#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAKEFILE = ROOT / "Makefile"
REQUIRED_FILES = [
    "scripts/release/current-full-live-release-gate.sh",
    "scripts/acceptance/current-full-live-release-gate.mjs",
    "docs/current/development/full-live-release-gate.md",
]
REQUIRED_SCRIPT_TOKENS = [
    "Clean Stack",
    "Fresh DB Migration",
    "Upgrade DB Migration",
    "SourceSystem-only Event",
    "Assignment",
    "Delivery",
    "ACK",
    "Result",
    "COMPLETED",
    "collect_evidence",
    "postgres-dump.sql",
    "http-transcript.json",
    "CURRENT_FULL_LIVE_RELEASE_GATE_MODE",
]
REQUIRED_ACCEPTANCE_TOKENS = [
    "/admin/source-systems",
    "/admin/dispatch-flows/agent-pools",
    "/admin/dispatch-flows",
    "/api/events/intake",
    "/api/dispatch-requests/",
    "/callback-inbox?limit=100",
    "/callback-inbox/summary?limit=100",
    "SOURCE_FLOW_DEFAULT_POOL",
    "SOURCE_DEFAULT",
    "LOWEST_LOAD",
    "requiredCapabilities",
    "Capability gates",
]


def target_body(text: str, target: str) -> str:
    pattern = re.compile(rf"^{re.escape(target)}:\s*(.*?)\n(?=^[A-Za-z0-9_.\-]+:|\Z)", re.M | re.S)
    match = pattern.search(text)
    return match.group(1) if match else ""


def fail(message: str) -> int:
    print(f"Phase 7B full live release gate verification failed: {message}", file=sys.stderr)
    return 1


def require_text(path: Path, token: str) -> int | None:
    text = path.read_text()
    if token not in text:
        return fail(f"{path.relative_to(ROOT)} missing token: {token}")
    return None


def main() -> int:
    makefile = MAKEFILE.read_text()
    for target in ["verify-e2e", "verify-live-e2e", "verify-e2e-dry-run", "verify-release", "verify-fast"]:
        if not re.search(rf"^{re.escape(target)}:", makefile, flags=re.M):
            return fail(f"missing Makefile target {target}")
    e2e = target_body(makefile, "verify-e2e")
    if "current-full-live-release-gate.sh" not in e2e:
        return fail("verify-e2e must run current-full-live-release-gate.sh")
    if "CURRENT_FULL_LIVE_RELEASE_GATE_MODE=$${CURRENT_FULL_LIVE_RELEASE_GATE_MODE:-live}" not in e2e:
        return fail("verify-e2e must default to live mode")
    dry = target_body(makefile, "verify-e2e-dry-run")
    if "CURRENT_FULL_LIVE_RELEASE_GATE_MODE=dry-run" not in dry:
        return fail("verify-e2e-dry-run must explicitly run dry-run contract mode")
    live = target_body(makefile, "verify-live-e2e")
    if "CURRENT_FULL_LIVE_RELEASE_GATE_MODE=live" not in live:
        return fail("verify-live-e2e must explicitly run live mode")
    release = target_body(makefile, "verify-release")
    if "verify-e2e" not in release or "verify-release-hygiene" not in release:
        return fail("verify-release must include verify-e2e and verify-release-hygiene")
    fast = target_body(makefile, "verify-fast")
    if "verify-phase7b-full-live-release-gate.py" not in fast:
        return fail("verify-fast must include the Phase 7B static contract verifier")

    for rel in REQUIRED_FILES:
        path = ROOT / rel
        if not path.exists():
            return fail(f"missing required file {rel}")

    release_script = ROOT / "scripts/release/current-full-live-release-gate.sh"
    acceptance = ROOT / "scripts/acceptance/current-full-live-release-gate.mjs"
    for token in REQUIRED_SCRIPT_TOKENS:
        maybe = require_text(release_script, token)
        if maybe is not None:
            return maybe
    for token in REQUIRED_ACCEPTANCE_TOKENS:
        maybe = require_text(acceptance, token)
        if maybe is not None:
            return maybe
    if not release_script.stat().st_mode & 0o111:
        return fail("current-full-live-release-gate.sh must be executable")
    if not acceptance.stat().st_mode & 0o111:
        return fail("current-full-live-release-gate.mjs must be executable")
    print("Phase 7B full live release gate contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
