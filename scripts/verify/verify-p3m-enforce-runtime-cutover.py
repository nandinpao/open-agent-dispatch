#!/usr/bin/env python3
"""Verify P3-M ENFORCE runtime acceptance / release cutover wiring."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADMIN = ROOT / "ai-event-gateway-admin-ui"


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def main() -> int:
    script = ADMIN / "scripts" / "verify-p3m-runtime-cutover.mjs"
    if not script.is_file():
        fail(f"Missing required Admin UI P3-M verification script: {script.relative_to(ROOT)}")
    result = subprocess.run(["node", str(script)], cwd=ADMIN)
    if result.returncode != 0:
        fail("P3-M ENFORCE runtime acceptance / release cutover verification failed")
    print("OK P3-M ENFORCE runtime acceptance / release cutover wiring")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
