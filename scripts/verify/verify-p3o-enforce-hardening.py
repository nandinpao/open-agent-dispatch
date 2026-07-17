#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADMIN = ROOT / "ai-event-gateway-admin-ui"


def main() -> int:
    script = ADMIN / "scripts" / "verify-p3o-enforce-hardening.mjs"
    if not script.is_file():
        print(f"[FAIL] Missing {script.relative_to(ROOT)}", file=sys.stderr)
        return 1
    return subprocess.run(["node", str(script)], cwd=ADMIN).returncode


if __name__ == "__main__":
    raise SystemExit(main())
