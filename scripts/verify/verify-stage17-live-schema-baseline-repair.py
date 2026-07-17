#!/usr/bin/env python3
"""Verify Phase 17 live schema baseline repair.

The clean baseline must still satisfy the active schedulers/mappers used during
live agent bootstrap. This verifier prevents the SQL grammar failures observed
from missing outbox, adapter action, gateway lease, runtime state, and remaining
support-only remediation persistence columns.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"

REQUIRED_TOKENS = [
    # module_outbox_events mapper contract
    "alter table module_outbox_events add column if not exists outbox_id",
    "alter table module_outbox_events add column if not exists attempt_count",
    "alter table module_outbox_events add column if not exists next_attempt_at",
    "alter table module_outbox_events add column if not exists claimed_by",
    "alter table module_outbox_events add column if not exists claim_until",
    "alter table module_outbox_events add column if not exists published_at",
    # integration_event_outbox mapper contract
    "alter table integration_event_outbox add column if not exists integration_event_id",
    "alter table integration_event_outbox add column if not exists envelope_json",
    "alter table integration_event_outbox add column if not exists next_attempt_at",
    "alter table integration_event_outbox add column if not exists claim_until",
    # gateway node lease scheduler contract
    "alter table gateway_nodes add column if not exists node_name",
    "alter table gateway_nodes add column if not exists lease_expires_at",
    "alter table gateway_nodes add column if not exists last_heartbeat_at",
    # adapter action scheduler contract
    "alter table adapter_actions add column if not exists idempotency_key",
    "alter table adapter_actions add column if not exists adapter_type",
    "alter table adapter_actions add column if not exists next_attempt_at",
    "alter table adapter_actions add column if not exists lease_expires_at",
    "alter table adapter_actions add column if not exists worker_heartbeat_at",
    "alter table adapter_actions add column if not exists payload_json",
    # runtime state tables
    "create table if not exists agent_runtime_capability_profiles",
    "create table if not exists agent_runtime_capability_items",
    "create table if not exists agent_runtime_load_snapshots",
    "create table if not exists agent_runtime_descriptors",
    "create table if not exists runtime_feature_catalog",
    "create table if not exists agent_runtime_feature_observations",
    "create table if not exists agent_runtime_feature_trust",
    # runtime resource/binding compatibility columns
    "alter table runtime_resources add column if not exists runtime_code",
    "alter table agent_runtime_bindings add column if not exists runtime_code",
    "alter table agent_runtime_bindings add column if not exists capacity_limit",
    # remaining support-only remediation lease scheduler persistence
    "create table if not exists agent_remediation_workflows",
    "create table if not exists agent_remediation_workflow_history",
    "create table if not exists agent_remediation_workflow_action_executions",
    "execution_lease_expires_at",
]

FORBIDDEN_IN_LIVE_GATE = [
    # These old offer states must remain absent even after schema repair.
    "task_" + "offers",
    "offer_" + "first",
    "OFFER" + "ING",
]


def main() -> int:
    if not BASELINE.exists():
        print(f"ERROR baseline missing: {BASELINE}", file=sys.stderr)
        return 1
    text = BASELINE.read_text(encoding="utf-8")
    lower = text.lower()
    missing = [token for token in REQUIRED_TOKENS if token.lower() not in lower]
    forbidden = [token for token in FORBIDDEN_IN_LIVE_GATE if token.lower() in lower]
    if missing:
        print("ERROR Phase 17 baseline missing required live-schema tokens:", file=sys.stderr)
        for token in missing:
            print(f" - {token}", file=sys.stderr)
        return 1
    if forbidden:
        print("ERROR Phase 17 baseline reintroduced forbidden " + "offer" + "-" + "first tokens:", file=sys.stderr)
        for token in forbidden:
            print(f" - {token}", file=sys.stderr)
        return 1
    print("Stage 17 live schema baseline repair contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
