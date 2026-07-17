#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"
COUNT="${1:-100}"
RUN_ID="${2:-$(date +%Y%m%d%H%M%S)-$$}"
OBJECT_ID="EQP-RELIABILITY-${RUN_ID}"

post_event() {
  local idx="$1"
  cat <<JSON | curl -sS -X POST "${BASE_URL}/api/events/intake" \
    -H 'Content-Type: application/json' \
    --data-binary @-
{
  "tenantId": "tenant-a",
  "sourceSystem": "MES",
  "siteId": "TNN",
  "plantId": "TNN-FAB-01",
  "objectType": "EQUIPMENT",
  "objectId": "${OBJECT_ID}",
  "eventType": "EQUIPMENT_ALARM",
  "errorCode": "TEMP_HIGH",
  "severity": "HIGH",
  "message": "Chamber temperature over threshold",
  "attributes": {
    "smokeRunId": "${RUN_ID}",
    "sequence": ${idx}
  }
}
JSON
}

printf 'Core status: '
curl -fsS "${BASE_URL}/api/core/status" >/dev/null
echo "OK"

echo "Posting ${COUNT} duplicate events for objectId=${OBJECT_ID}"
LAST_RESPONSE=""
for i in $(seq 1 "${COUNT}"); do
  LAST_RESPONSE="$(post_event "$i")"
  if [ $((i % 25)) -eq 0 ] || [ "$i" -eq "$COUNT" ]; then
    echo "  posted ${i}/${COUNT}"
  fi
done

export LAST_RESPONSE OBJECT_ID BASE_URL COUNT
python3 <<'PY'
import json
import os
import sys
import urllib.request

base_url = os.environ["BASE_URL"]
object_id = os.environ["OBJECT_ID"]
expected = int(os.environ["COUNT"])
last_envelope = json.loads(os.environ["LAST_RESPONSE"])
last = last_envelope.get('data', last_envelope) if isinstance(last_envelope, dict) else last_envelope

with urllib.request.urlopen(f"{base_url}/api/incidents?objectId={object_id}&limit=100") as response:
    incidents_envelope = json.load(response)
incidents = incidents_envelope.get('data', incidents_envelope) if isinstance(incidents_envelope, dict) else incidents_envelope
incident_id = last.get("incidentId")
if incident_id:
    with urllib.request.urlopen(f"{base_url}/api/tasks/incident/{incident_id}?limit=100") as response:
        tasks_envelope = json.load(response)
        tasks = tasks_envelope.get('data', tasks_envelope) if isinstance(tasks_envelope, dict) else tasks_envelope
else:
    tasks = []

errors = []
if len(incidents) != 1:
    errors.append(f"expected 1 incident, got {len(incidents)}")
if len(tasks) > 1:
    errors.append(f"expected <= 1 open-created task for one repeated incident, got {len(tasks)}")
if int(last.get("occurrenceCount", 0)) != expected:
    errors.append(f"expected last occurrenceCount={expected}, got {last.get('occurrenceCount')}")
if not last.get("duplicate"):
    errors.append("expected final response duplicate=true")

summary = {
    "objectId": object_id,
    "eventsPosted": expected,
    "incidentCount": len(incidents),
    "taskCount": len(tasks),
    "lastOccurrenceCount": last.get("occurrenceCount"),
    "incidentId": last.get("incidentId"),
    "taskId": last.get("taskId"),
    "taskCreated": last.get("taskCreated"),
}
print(json.dumps(summary, ensure_ascii=False, indent=2))
if errors:
    for error in errors:
        print("ERROR:", error, file=sys.stderr)
    sys.exit(1)
PY
