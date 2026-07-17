#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
HOTFIX = ROOT / "scripts/db/phase27-live-event-intake-runtime-schema-repair.sql"
MAKEFILE = ROOT / "Makefile"

failures = []

def require(condition, message):
    if not condition:
        failures.append(message)

def require_text(path, tokens):
    text = path.read_text()
    for token in tokens:
        require(token in text, f"{path.relative_to(ROOT)} missing token: {token}")
    return text

baseline = require_text(BASELINE, [
    "create table if not exists event_dedup_state",
    "fingerprint varchar(255) primary key",
    "active_incident_id varchar(128)",
    "occurrence_count int not null default 1",
    "last_event_id varchar(128)",
    "last_message text",
    "expires_at timestamptz",
    "updated_at timestamptz not null default now()",
    "create table if not exists task_callbacks",
    "processed_at timestamptz not null default now()",
    "idempotency_key varchar(255)",
    "callback_fingerprint varchar(255)",
    "replay_detected boolean not null default false",
    "owner_gateway_node_id varchar(128)",
    "agent_session_id varchar(128)",
    "plugin_version varchar(255)",
    "capability_revision varchar(255)",
])

require("dedup_key varchar(255) primary key" not in baseline, "event_dedup_state must not use retired dedup_key primary key")
require("plugin_version varchar(64)" not in baseline, "agents.plugin_version must not remain varchar(64)")
require("capability_revision varchar(64)" not in baseline, "agents.capability_revision must not remain varchar(64)")

hotfix = require_text(HOTFIX, [
    "alter table agents alter column plugin_version type varchar(255)",
    "alter table agents alter column capability_revision type varchar(255)",
    "alter table task_callbacks add column if not exists processed_at",
    "alter table task_callbacks add column if not exists idempotency_key",
    "alter table event_dedup_state add column if not exists fingerprint",
    "create unique index if not exists ux_event_dedup_state_fingerprint",
    "alter table event_dedup_state add column if not exists active_incident_id",
    "alter table event_dedup_state add column if not exists updated_at",
])

makefile = require_text(MAKEFILE, [
    "verify-stage27-event-intake-runtime-schema-repair",
    "verify-stage27-event-intake-runtime-schema-repair.py",
])

if failures:
    for failure in failures:
        print(f"[stage27] FAIL: {failure}", file=sys.stderr)
    sys.exit(1)
print("Stage 27 event intake/runtime schema repair contract verified.")
