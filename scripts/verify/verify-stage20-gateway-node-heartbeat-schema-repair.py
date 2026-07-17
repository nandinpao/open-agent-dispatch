#!/usr/bin/env python3
"""Verify Phase 20 gateway node heartbeat schema repair."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
DAO = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent/GatewayNodeDao.xml"
CONVERTER = ROOT / "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/agent/converter/GatewayNodePersistenceConverter.java"
SERVICE = ROOT / "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/gateway/GatewayDirectoryService.java"
HOTFIX = ROOT / "scripts/db/phase20-live-gateway-node-version-repair.sql"
DOC = ROOT / "docs/PHASE20_GATEWAY_NODE_HEARTBEAT_SCHEMA_REPAIR.md"


def table_block(text: str, name: str) -> str:
    match = re.search(rf"create table if not exists {re.escape(name)} \((.*?)\);", text, re.S | re.I)
    return match.group(1).lower() if match else ""


def main() -> int:
    errors: list[str] = []
    for path in [BASELINE, DAO, CONVERTER, SERVICE, HOTFIX, DOC]:
        if not path.exists():
            errors.append(f"missing file: {path.relative_to(ROOT)}")
    if errors:
        print("Stage 20 gateway node heartbeat schema repair failed:", file=sys.stderr)
        for e in errors:
            print(f" - {e}", file=sys.stderr)
        return 1

    baseline = BASELINE.read_text(encoding="utf-8", errors="ignore")
    baseline_lower = baseline.lower()
    block = table_block(baseline, "gateway_nodes")
    dao = DAO.read_text(encoding="utf-8", errors="ignore").lower()
    converter = CONVERTER.read_text(encoding="utf-8", errors="ignore")
    service = SERVICE.read_text(encoding="utf-8", errors="ignore")
    hotfix = HOTFIX.read_text(encoding="utf-8", errors="ignore").lower()
    doc = DOC.read_text(encoding="utf-8", errors="ignore").lower()

    if not block:
        errors.append("baseline does not create gateway_nodes table")
    for token in [
        "version varchar(64) not null default 'unknown'",
        "lease_expires_at timestamptz",
        "last_heartbeat_at timestamptz",
    ]:
        if token not in block:
            errors.append(f"gateway_nodes baseline block missing: {token}")
    if "version int" in block:
        errors.append("gateway_nodes version must not be int; mapper/domain use software version string")
    for token in ["coalesce(#{node.version}, 'unknown')", "version = coalesce(excluded.version, gateway_nodes.version, 'unknown')"]:
        if token not in dao:
            errors.append(f"GatewayNodeDao missing null-safe token: {token}")
    if 'node.getVersion() == null || node.getVersion().isBlank() ? "unknown"' not in converter:
        errors.append("GatewayNodePersistenceConverter does not default outbound null version to unknown")
    if 'po.getVersion() == null || po.getVersion().isBlank() ? "unknown"' not in converter:
        errors.append("GatewayNodePersistenceConverter does not default inbound null version to unknown")
    if 'node.setVersion("unknown")' not in service:
        errors.append("GatewayDirectoryService does not default heartbeat/register version to unknown")
    for token in [
        "alter table gateway_nodes alter column version type varchar(64) using version::text",
        "update gateway_nodes set version = 'unknown' where version is null",
        "alter table gateway_nodes alter column version set not null",
    ]:
        if token not in hotfix:
            errors.append(f"hotfix missing: {token}")
    for token in ["api_data_integrity_violation", "gateway", "version", "unknown"]:
        if token not in doc:
            errors.append(f"Phase 20 doc missing token: {token}")

    if errors:
        print("Stage 20 gateway node heartbeat schema repair failed:", file=sys.stderr)
        for e in errors:
            print(f" - {e}", file=sys.stderr)
        return 1
    print("Stage 20 gateway node heartbeat schema repair contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
