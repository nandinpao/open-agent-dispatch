#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
COUNT="${1:-100}"

status_json="$(curl -fsS "${BASE_URL}/api/core/status")"
export STATUS_JSON="$status_json"
python3 <<'PY'
import json
import os
import sys
status = json.loads(os.environ['STATUS_JSON'])
if status.get('dedupStore') != 'REDISSON':
    print(json.dumps(status, ensure_ascii=False, indent=2))
    print(f"ERROR: expected dedupStore=REDISSON, got {status.get('dedupStore')}", file=sys.stderr)
    sys.exit(1)
print(f"Core dedupStore={status.get('dedupStore')} version={status.get('version')}")
PY

"$(dirname "$0")/event-reliability-smoke.sh" "$COUNT" "redisson-$(date +%Y%m%d%H%M%S)-$$"
