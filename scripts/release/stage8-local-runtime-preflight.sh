#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

ENV_FILE="${STAGE1_ENV_FILE:-${ENV_FILE:-}}"
if [[ -z "$ENV_FILE" ]]; then
  if [[ -f deploy/env/.env.local ]]; then
    ENV_FILE="deploy/env/.env.local"
  else
    ENV_FILE="deploy/env/.env.local.example"
  fi
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Stage 8 local runtime preflight failed: env file not found: $ENV_FILE" >&2
  exit 2
fi

# shellcheck source=scripts/ci/env-utils.sh
source "$ROOT/scripts/ci/env-utils.sh"
load_dotenv_file "$ENV_FILE"

CORE_URL="${CORE_URL:-${CORE_BASE_URL:-http://127.0.0.1:${CORE_HTTP_PORT:-18080}}}"
CORE_URL="${CORE_URL%/}"
CSRF_URL="${CORE_URL}/api/auth/csrf"
ATTEMPTS="${STAGE8_CORE_READY_ATTEMPTS:-60}"
INTERVAL_SECONDS="${STAGE8_CORE_READY_INTERVAL_SECONDS:-2}"
HTTP_TIMEOUT_SECONDS="${STAGE8_CORE_READY_HTTP_TIMEOUT_SECONDS:-3}"
PROJECT_NAME_FOR_DIAG="${PROJECT_NAME:-opendispatch}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.local.yml}"

printf 'Stage 8 local runtime preflight: waiting for Core Admin CSRF at %s' "$CSRF_URL"
set +e
python3 - "$CSRF_URL" "$ATTEMPTS" "$INTERVAL_SECONDS" "$HTTP_TIMEOUT_SECONDS" <<'PY'
import json
import sys
import time
import urllib.error
import urllib.request

url = sys.argv[1]
attempts = int(sys.argv[2])
interval = float(sys.argv[3])
timeout = float(sys.argv[4])
last_error = ""
last_status = None
last_body = ""

for attempt in range(1, attempts + 1):
    try:
        request = urllib.request.Request(url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(request, timeout=timeout) as response:
            last_status = response.status
            raw = response.read(4096).decode("utf-8", errors="replace")
            last_body = raw
            try:
                body = json.loads(raw or "{}")
            except Exception:
                body = {}
            data = body.get("data") if isinstance(body, dict) else None
            payload = data if isinstance(data, dict) else body
            if response.status == 200 and isinstance(payload, dict) and payload.get("headerName") and payload.get("token"):
                print(" OK")
                sys.exit(0)
            last_error = f"HTTP {response.status}; body={raw[:500]}"
    except urllib.error.HTTPError as exc:
        last_status = exc.code
        try:
            last_body = exc.read(4096).decode("utf-8", errors="replace")
        except Exception:
            last_body = ""
        last_error = f"HTTP {exc.code}; body={last_body[:500]}"
    except Exception as exc:
        last_error = str(exc)
    if attempt < attempts:
        print(".", end="", flush=True)
        time.sleep(interval)

print(" FAILED")
print(f"lastStatus={last_status or 'n/a'}", file=sys.stderr)
print(f"lastError={last_error or 'n/a'}", file=sys.stderr)
if last_body:
    print("lastBody=", last_body[:1200], file=sys.stderr)
sys.exit(1)
PY
status=$?
set -e

if [[ "$status" -eq 0 ]]; then
  exit 0
fi

echo "" >&2
echo "Stage 8 local runtime preflight failed." >&2
echo "Core is not ready at: $CORE_URL" >&2
echo "The strict Stage 1 Golden Path cannot authenticate because /api/auth/csrf is unreachable or invalid." >&2
echo "" >&2
echo "Recommended fixes:" >&2
echo "  1. Let the Stage 8 gate create an isolated fresh local stack:" >&2
echo "     STAGE8_MANAGED_LOCAL_STACK=true make stage8-release-gate" >&2
echo "" >&2
echo "  2. Or start the normal local stack before rerunning the gate:" >&2
echo "     make down-v && make up-agent && make stage8-release-gate" >&2
echo "" >&2
echo "Diagnostics:" >&2
if command -v docker >/dev/null 2>&1; then
  docker compose -p "$PROJECT_NAME_FOR_DIAG" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps >&2 || true
  docker compose -p "$PROJECT_NAME_FOR_DIAG" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=160 core core-db-migrate postgres redis netty mock-agent >&2 || true
else
  echo "docker command is not available; run make check-toolchain first." >&2
fi
exit "$status"
