#!/usr/bin/env bash
set -euo pipefail

CORE_ADMIN_BASE_URL="${CORE_ADMIN_BASE_URL:-http://127.0.0.1:18080}"
TASK_ID="${TASK_ID:-${1:-}}"
DISPATCH_REQUEST_ID="${DISPATCH_REQUEST_ID:-}"
MIN_CALLBACKS="${MIN_CALLBACKS:-1}"
REQUIRE_TERMINAL_CALLBACK="${REQUIRE_TERMINAL_CALLBACK:-1}"
REQUIRE_LEDGER_TERMINAL="${REQUIRE_LEDGER_TERMINAL:-0}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-60}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-3}"
OUT_DIR="${OUT_DIR:-.runtime/topology-acceptance}"
LEDGER_PATH=""
INBOX_PATH=""
SUMMARY_PATH=""

usage() {
  cat <<USAGE
Usage: TASK_ID=<task-id> [DISPATCH_REQUEST_ID=<dispatch-request-id>] $0 [task-id]

Checks dynamic topology recovery acceptance against Core persisted truth:
  1. Dispatch Ledger exists for the task or dispatch request.
  2. Callback Inbox has callback records for the task or dispatch request.
  3. Optional terminal RESULT / ERROR callback is present.
  4. Optional ledger terminal state is present.

Defaults:
  CORE_ADMIN_BASE_URL=${CORE_ADMIN_BASE_URL}
  TIMEOUT_SECONDS=${TIMEOUT_SECONDS}
  POLL_INTERVAL_SECONDS=${POLL_INTERVAL_SECONDS}
  MIN_CALLBACKS=${MIN_CALLBACKS}
  REQUIRE_TERMINAL_CALLBACK=${REQUIRE_TERMINAL_CALLBACK}
  REQUIRE_LEDGER_TERMINAL=${REQUIRE_LEDGER_TERMINAL}

This script intentionally reads Core Dispatch Ledger / Callback Inbox only.
Gateway node diagnostics are never treated as task/callback truth.
USAGE
}

if [[ "${TASK_ID}" == "-h" || "${TASK_ID}" == "--help" || "${TASK_ID}" == "help" ]]; then
  usage
  exit 0
fi

if [[ -z "${TASK_ID}" && -z "${DISPATCH_REQUEST_ID}" ]]; then
  echo "ERROR: TASK_ID or DISPATCH_REQUEST_ID is required." >&2
  usage >&2
  exit 2
fi

mkdir -p "${OUT_DIR}"

urlencode() {
  python3 - "$1" <<'PYURL'
import sys, urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=''))
PYURL
}

fetch() {
  local path="$1"
  local dest="$2"
  local url="${CORE_ADMIN_BASE_URL}${path}"
  curl -fsS "${url}" -o "${dest}"
}

normalize_endpoint_ids() {
  if [[ -n "${TASK_ID}" ]]; then
    local encoded_task
    encoded_task="$(urlencode "${TASK_ID}")"
    LEDGER_PATH="/admin/tasks/${encoded_task}/dispatch-ledger"
    INBOX_PATH="/admin/tasks/${encoded_task}/callback-inbox"
    SUMMARY_PATH="/admin/tasks/${encoded_task}/callback-inbox/summary"
  else
    local encoded_dispatch
    encoded_dispatch="$(urlencode "${DISPATCH_REQUEST_ID}")"
    LEDGER_PATH="/admin/dispatch-requests/${encoded_dispatch}/ledger"
    INBOX_PATH="/admin/dispatch-requests/${encoded_dispatch}/callback-inbox"
    SUMMARY_PATH="/admin/dispatch-requests/${encoded_dispatch}/callback-inbox/summary"
  fi
}

check_json() {
  local ledger_file="$1"
  local inbox_file="$2"
  local summary_file="$3"
  python3 - "$ledger_file" "$inbox_file" "$summary_file" "${MIN_CALLBACKS}" "${REQUIRE_TERMINAL_CALLBACK}" "${REQUIRE_LEDGER_TERMINAL}" <<'PYCHECK'
from __future__ import annotations
import json, sys
from pathlib import Path

def load(path: str):
    text = Path(path).read_text(encoding='utf-8')
    if not text.strip():
        return None
    data = json.loads(text)
    while isinstance(data, dict) and len(data) == 1 and any(k in data for k in ('data', 'payload', 'result')):
        data = data.get('data') or data.get('payload') or data.get('result')
    return data

def as_list(value):
    if value is None:
        return []
    if isinstance(value, list):
        return value
    if isinstance(value, dict):
        for key in ('content', 'items', 'records', 'rows', 'data'):
            if isinstance(value.get(key), list):
                return value[key]
        return [value]
    return []

ledger = as_list(load(sys.argv[1]))
inbox = as_list(load(sys.argv[2]))
summary = load(sys.argv[3])
min_callbacks = int(sys.argv[4])
require_terminal = sys.argv[5].lower() in {'1', 'true', 'yes'}
require_ledger_terminal = sys.argv[6].lower() in {'1', 'true', 'yes'}

if not ledger:
    raise SystemExit('Dispatch Ledger is empty. Core dispatch truth was not created or endpoint returned no records.')
if len(inbox) < min_callbacks:
    raise SystemExit(f'Callback Inbox has {len(inbox)} record(s), expected at least {min_callbacks}.')

terminal_types = {'TASK_RESULT', 'RESULT', 'TASK_ERROR', 'ERROR'}
callback_types = {str(item.get('callbackType') or item.get('eventType') or item.get('type') or '').upper() for item in inbox if isinstance(item, dict)}
if require_terminal and not (callback_types & terminal_types):
    raise SystemExit(f'Callback Inbox has no terminal RESULT/ERROR callback. Seen callback types: {sorted(callback_types)}')

if require_ledger_terminal:
    terminal_states = {'RESULT_RECEIVED', 'ERROR_RECEIVED', 'COMPLETED', 'FAILED', 'TASK_COMPLETED', 'TASK_FAILED'}
    seen = set()
    for item in ledger:
        if not isinstance(item, dict):
            continue
        for key in ('resultState', 'callbackState', 'dispatchStatus', 'state', 'status'):
            value = item.get(key)
            if value is not None:
                seen.add(str(value).upper())
    if not (seen & terminal_states):
        raise SystemExit(f'Dispatch Ledger has no terminal state. Seen states: {sorted(seen)}')

summary_status = None
if isinstance(summary, dict):
    summary_status = summary.get('terminalCallbackType') or summary.get('status') or summary.get('processStatus')

print('ACCEPTED dynamic topology recovery')
print(f'ledger_records={len(ledger)}')
print(f'callback_records={len(inbox)}')
print(f'callback_types={sorted(callback_types)}')
if summary_status is not None:
    print(f'summary_status={summary_status}')
PYCHECK
}

normalize_endpoint_ids

started_at=$(date +%s)
attempt=0
while true; do
  attempt=$((attempt + 1))
  ledger_file="${OUT_DIR}/ledger-${attempt}.json"
  inbox_file="${OUT_DIR}/callback-inbox-${attempt}.json"
  summary_file="${OUT_DIR}/callback-summary-${attempt}.json"
  echo "==> Acceptance poll #${attempt}: Core Dispatch Ledger + Callback Inbox"
  if fetch "${LEDGER_PATH}" "${ledger_file}" && fetch "${INBOX_PATH}" "${inbox_file}" && fetch "${SUMMARY_PATH}" "${summary_file}"; then
    if output="$(check_json "${ledger_file}" "${inbox_file}" "${summary_file}" 2>&1)"; then
      echo "${output}"
      echo "Artifacts written under ${OUT_DIR}"
      exit 0
    fi
    echo "${output}"
  else
    echo "Core API not ready or endpoint failed. Retrying..."
  fi
  now=$(date +%s)
  if (( now - started_at >= TIMEOUT_SECONDS )); then
    echo "ERROR: Dynamic topology recovery acceptance timed out after ${TIMEOUT_SECONDS}s." >&2
    echo "Check ${OUT_DIR} for the latest ledger / callback inbox payloads." >&2
    exit 1
  fi
  sleep "${POLL_INTERVAL_SECONDS}"
done
