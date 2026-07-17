#!/usr/bin/env python3
"""Verify Phase 18 incident lifecycle schema repair."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
DAO = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/incident/IncidentDao.xml"
HOTFIX = ROOT / "scripts/db/phase18-live-incident-schema-repair.sql"
DOC = ROOT / "docs/PHASE18_INCIDENT_LIFECYCLE_SCHEMA_REPAIR.md"

REQUIRED_COLUMNS = [
    "incident_id",
    "fingerprint",
    "tenant_id",
    "source_system",
    "site_id",
    "plant_id",
    "object_type",
    "object_id",
    "event_type",
    "error_code",
    "severity",
    "status",
    "first_seen_at",
    "last_seen_at",
    "occurrence_count",
    "last_message",
    "linked_task_id",
    "linked_issue_id",
    "resolved_at",
    "reopened_at",
    "reopen_count",
    "lifecycle_reason",
]
REQUIRED_INDEXES = [
    "idx_incidents_active_last_seen",
    "idx_incidents_fingerprint_last_seen",
    "idx_incidents_tenant_status",
]


def incident_table_block(text: str) -> str:
    match = re.search(r"create table if not exists incidents \((.*?)\);", text, re.S | re.I)
    return match.group(1).lower() if match else ""


def main() -> int:
    errors: list[str] = []
    for path in [BASELINE, DAO, HOTFIX, DOC]:
        if not path.exists():
            errors.append(f"missing file: {path.relative_to(ROOT)}")
    if errors:
        print("Stage 18 incident lifecycle schema repair failed:", file=sys.stderr)
        for e in errors:
            print(f" - {e}", file=sys.stderr)
        return 1

    baseline = BASELINE.read_text(encoding="utf-8", errors="ignore").lower()
    dao = DAO.read_text(encoding="utf-8", errors="ignore").lower()
    hotfix = HOTFIX.read_text(encoding="utf-8", errors="ignore").lower()
    doc = DOC.read_text(encoding="utf-8", errors="ignore").lower()
    block = incident_table_block(baseline)

    if not block:
        errors.append("baseline does not create incidents table")
    for col in REQUIRED_COLUMNS:
        if col not in block:
            errors.append(f"baseline incidents table missing column: {col}")
        if col not in dao:
            errors.append(f"IncidentDao no longer references expected column: {col}")
        if f"add column if not exists {col}" not in hotfix and col != "incident_id":
            errors.append(f"hotfix script missing add-column repair for: {col}")
    for idx in REQUIRED_INDEXES:
        if idx not in baseline:
            errors.append(f"baseline missing incident index: {idx}")
        if idx not in hotfix:
            errors.append(f"hotfix missing incident index: {idx}")
    for token in ["last_seen_at", "incident lifecycle", "phase18-live-incident-schema-repair.sql"]:
        if token not in doc:
            errors.append(f"Phase 18 doc missing token: {token}")

    if errors:
        print("Stage 18 incident lifecycle schema repair failed:", file=sys.stderr)
        for e in errors:
            print(f" - {e}", file=sys.stderr)
        return 1
    print("Stage 18 incident lifecycle schema repair contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
