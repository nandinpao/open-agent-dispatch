#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAKEFILE = ROOT / "Makefile"
REQUIRED_TARGETS = [
    "verify-fast",
    "verify-build",
    "verify-integration",
    "verify-e2e",
    "verify-release",
    "verify",
    "archive-verify-current-legacy",
    "archive-verify-stage8-release-gate",
    "archive-verify-phase32",
]
REQUIRED_FILES = [
    "scripts/release/current-minimum-live-golden-path.sh",
    "scripts/verify/verify-release-hygiene.py",
    "docs/current/development/verification-guide.md",
]


def target_body(text: str, target: str) -> str:
    pattern = re.compile(rf"^{re.escape(target)}:\s*(.*?)\n(?=^[A-Za-z0-9_.\-]+:|\Z)", re.M | re.S)
    match = pattern.search(text)
    return match.group(1) if match else ""


def fail(message: str) -> int:
    print(f"Phase 7A verification governance failed: {message}", file=sys.stderr)
    return 1


def main() -> int:
    text = MAKEFILE.read_text()
    for target in REQUIRED_TARGETS:
        if not re.search(rf"^{re.escape(target)}:", text, flags=re.M):
            return fail(f"missing Makefile target {target}")
    verify_body = target_body(text, "verify")
    if "verify-release" not in verify_body:
        return fail("make verify must delegate to verify-release")
    current_body = target_body(text, "verify-current")
    if "verify-release" not in current_body:
        return fail("verify-current must be a compatibility alias to verify-release")
    if any(token in current_body for token in ["stage8", "phase32", "verify-current-app-contract"]):
        return fail("verify-current must not call archived Stage/Phase verifier targets")
    release_body = target_body(text, "verify-release")
    for token in ["verify-fast", "verify-build", "verify-integration", "verify-e2e", "verify-release-hygiene"]:
        if token not in release_body:
            return fail(f"verify-release must include {token}")
    fast_body = target_body(text, "verify-fast")
    for token in [
        "verify-current-ui-semantics.py",
        "verify-phase5b-dispatch-workspace-shell.py",
        "verify-phase6c-issue-dedup-timeline.py",
        "verify-phase7a-current-verification-governance.py",
    ]:
        if token not in fast_body:
            return fail(f"verify-fast missing {token}")
    e2e_body = target_body(text, "verify-e2e")
    if "current-minimum-live-golden-path.sh" not in e2e_body and "current-full-live-release-gate.sh" not in e2e_body:
        return fail("verify-e2e must run the current minimum/full live golden path wrapper")
    for required in REQUIRED_FILES:
        path = ROOT / required
        if not path.exists():
            return fail(f"missing required file {required}")
    print("Phase 7A current verification governance contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
