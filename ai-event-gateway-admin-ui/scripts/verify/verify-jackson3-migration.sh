#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

python3 - <<'PY'
from pathlib import Path
import sys

root = Path.cwd()
forbidden_imports = (
    "import com.fasterxml.jackson.databind.",
    "import com.fasterxml.jackson.core.type.",
    "import com.fasterxml.jackson.core.JsonProcessingException;",
    "import com.fasterxml.jackson.datatype.",
)
violations = []

compatibility_allowlist = {
    Path("ai-event-gateway-core-app/src/test/java/com/opensocket/aievent/core/container/SharedRedissonDedupAtomicContainerTest.java"),
}

for path in root.rglob("*.java"):
    if ("target" in path.parts
            or path.name == "Jackson3MigrationGovernanceTest.java"
            or path.relative_to(root) in compatibility_allowlist):
        continue
    source = path.read_text(errors="replace")
    for forbidden in forbidden_imports:
        if forbidden in source:
            violations.append(f"{path.relative_to(root)} contains {forbidden}")

for path in root.rglob("pom.xml"):
    pom = path.read_text(errors="replace")
    if ("<groupId>com.fasterxml.jackson.core</groupId>" in pom
            and "<artifactId>jackson-databind</artifactId>" in pom):
        violations.append(f"{path.relative_to(root)} declares Jackson 2 databind")
    if "<groupId>com.fasterxml.jackson.datatype</groupId>" in pom:
        violations.append(f"{path.relative_to(root)} declares a Jackson 2 datatype module")

if violations:
    print("Jackson 3 migration verification failed:", file=sys.stderr)
    for violation in violations:
        print(f"  - {violation}", file=sys.stderr)
    sys.exit(1)

print("Jackson 3 migration verification passed (shared Redisson compatibility boundary allowed).")
PY
